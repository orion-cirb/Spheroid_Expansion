/*
 * Analyze Spheroid expansion 
 * Author Philippe Mailly / Heloïse Monnet
 */


import ij.*;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Date;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Point3D;
import mcib3d.geom.Voxel3D;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureCentroid;
import mcib3d.image3d.ImageHandler;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.io.FilenameUtils;


public class Spheroid_Expansion implements PlugIn {

    private final boolean canceled = false;
    private String imageDir = "";
    private Spheroid_Expansion_Tools.Processing tools = new Spheroid_Expansion_Tools.Processing();
    
    
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            String imageDir = IJ.getDirectory("Choose images directory")+File.separator;
            if (imageDir == null) {
                return;
            }
            File inDir = new File(imageDir);
            // Find images with fileExt extension
            String fileExt = tools.findImageType(inDir);
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles.isEmpty()) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find chanels, image calibration
            reader.setId(imageFiles.get(0));
            String[] channelNames = tools.findChannels(imageFiles.get(0), meta, reader);
            tools.findImageCalib(meta);
            String[] channels = tools.dialog(channelNames);
            if(channels == null)
                return;
            
            // Create output folder
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            Date date = new Date();
            String outDirResults = imageDir + File.separator+ "Results_" + dateFormat.format(date) +  File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write headers results for results files
            FileWriter results = new FileWriter(outDirResults + "Results.xls", false);
            BufferedWriter outPutResults = new BufferedWriter(results);
            outPutResults.write("Image name\tSerie name\tSpheroid Area (µm2)\tSpheroid diameter (µm)\t#Nucleus\tNucleus Area (µm2)\tNucleus distance to spheroid center (µm)\n");
            outPutResults.flush();
            
            for (String f : imageFiles) {
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                String rootName = FilenameUtils.getBaseName(f);
                reader.setId(f);
                int series = reader.getSeriesCount();
                for (int s = 0; s < series; s++) {
                    reader.setSeries(s);
                    options.setSeriesOn(s, true);
                    String seriesName = meta.getImageName(s);
                    // open nucleus and Phaloidin image Z project and multiply
                    int indexCh = ArrayUtils.indexOf(channelNames, channels[0]);
                    ImagePlus imgNuc = BF.openImagePlus(options)[indexCh];
                    ImagePlus imgNucProj = tools.doZProjection(imgNuc, ZProjector.MAX_METHOD);
                    tools.closeImages(imgNuc);
                    // Open Phaloidin channel
                    indexCh = ArrayUtils.indexOf(channelNames,channels[1]);
                    ImagePlus imgSpheroid = BF.openImagePlus(options)[indexCh];
                    ImagePlus imgSpheroidProj = tools.doZProjection(imgSpheroid, ZProjector.MAX_METHOD);
                    tools.closeImages(imgSpheroid);
                    Object3DInt spheroidObj = tools.findSpheroid(imgNucProj, imgSpheroidProj);
                    // find Sholl center, starting
                    tools.computeCenterRadius(imgSpheroidProj, spheroidObj);
                    System.out.println("Spheroid center = ("+tools.shollCenterX*tools.cal.pixelWidth+", "+tools.shollCenterY*tools.cal.pixelHeight);
                    System.out.println("Spheroid radius = "+ tools.spheroidRad);
                    ResultsTable shollResults = tools.shollAnalysis(imgSpheroidProj);
                    tools.closeImages(imgSpheroidProj);
                    // Find nuclei 
                    // Mask spheroid in nucleus image
                    ImagePlus imgNucMasked = new Duplicator().run(imgNucProj);
                    imgNucMasked.setCalibration(tools.cal);
                    Objects3DIntPopulation nucPop = tools.stardistDetection(imgNucMasked);
                    tools.closeImages(imgNucMasked);
                    // Compute distances
                    ArrayList<Double> nucDist = tools.computeParameters(spheroidObj, nucPop);
                    tools.computeShollNucleus(nucDist, shollResults, outDirResults, rootName+"_"+seriesName);
                    // Save results
                    tools.saveResults(nucPop, spheroidObj, rootName, seriesName, nucDist, outPutResults);
                    tools.saveObjectsImage(imgNucProj, nucPop, spheroidObj, rootName+"_"+seriesName, outDirResults);
                    tools.closeImages(imgNucProj);
                    options.clearSeries();
                }
            }
            outPutResults.close();

        } catch (IOException ex) {
            Logger.getLogger(Spheroid_Expansion.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(Spheroid_Expansion.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}
