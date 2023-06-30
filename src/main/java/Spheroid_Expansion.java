import ij.*;
import ij.gui.Overlay;
import ij.gui.Roi;
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
import java.util.HashMap;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.lang.ArrayUtils;


/**
 * Analyze spheroid expansion in 2D
 * @author ORION-CIRB
 */
public class Spheroid_Expansion implements PlugIn {

    private Spheroid_Expansion_Tools.Tools tools = new Spheroid_Expansion_Tools.Tools();
    
    public void run(String arg) {
        try {
            if (!tools.checkInstalledModules()) {
                return;
            }
            
            String imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }
            
            // Find images with fileExt extension
            String fileExt = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles.isEmpty()) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.findImageCalib(meta);
            
            // Find channel names
            String[] channelNames = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Dialog box
            String[] channels = tools.dialog(channelNames);
            if(channels == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }
                
            // Create output folder
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String outDirResults = imageDir + File.separator+ "Results_" + dateFormat.format(new Date()) + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write headers in results files
            FileWriter fwSholl = new FileWriter(outDirResults + "shollResults.xls", false);
            BufferedWriter shollResults = new BufferedWriter(fwSholl);
            shollResults.write("Image name\tCircle ID\tCircle radius (µm)\tPhalloidin area (µm2)\tNb nuclei\n");
            shollResults.flush();
            FileWriter fwGlobal = new FileWriter(outDirResults + "globalResults.xls", false);
            BufferedWriter globalResults = new BufferedWriter(fwGlobal);
            globalResults.write("Image name\tSpheroid area (µm2)\tSpheroid radius (µm)\tPhalloidin area (µm2)\tNb nuclei\n");
            globalResults.flush();

            for (String f : imageFiles) {
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);

                for (int s = 0; s < reader.getSeriesCount(); s++) {
                    reader.setSeries(s);
                    options.setSeriesOn(s, true);
                    String seriesName = meta.getImageName(s);
                    tools.print("--- ANALYZING IMAGE " + seriesName + " ---");
                    
                    // Open DAPI channel and z-project it
                    int indexCh = ArrayUtils.indexOf(channelNames, channels[0]);
                    ImagePlus imgNuc = BF.openImagePlus(options)[indexCh];
                    ImagePlus imgNucProj = tools.doZProjection(imgNuc, ZProjector.MAX_METHOD);
                    tools.flushCloseImg(imgNuc);
                    
                    // Open Phalloidin channel and z-project it
                    indexCh = ArrayUtils.indexOf(channelNames,channels[1]);
                    ImagePlus imgSpheroid = BF.openImagePlus(options)[indexCh];
                    ImagePlus imgSpheroidProj = tools.doZProjection(imgSpheroid, ZProjector.MAX_METHOD);
                    tools.flushCloseImg(imgSpheroid);
                    
                    // Detect spheroid without protrusions and fit a circle on it
                    Roi spheroidRoi = tools.fitSpheroid(imgNucProj, imgSpheroidProj);
                    // Compute fitted circle radius and centroid
                    HashMap<String, Double> spheroidParams = tools.computeSpheroidParams(spheroidRoi);
                    
                    // Get phalloidin mask
                    ImagePlus phalloidinMask = tools.phalloidinMask(imgSpheroidProj);
                    
                    // Detect nuclei
                    Objects3DIntPopulation nucPop = tools.stardistDetection(imgNucProj, spheroidRoi);
                    
                    // Save results
                    Overlay shollOverlay = tools.writeShollResults(shollResults, spheroidRoi, spheroidParams, phalloidinMask, nucPop, seriesName);
                    tools.writeGlobalResults(globalResults, spheroidRoi, spheroidParams, phalloidinMask, nucPop, seriesName);
                    tools.drawResults(imgNucProj, nucPop, phalloidinMask, shollOverlay, outDirResults, seriesName);
                    
                    tools.flushCloseImg(imgNucProj);
                    tools.flushCloseImg(imgSpheroidProj);
                    tools.flushCloseImg(phalloidinMask);
                    options.clearSeries();
                }
            }
            shollResults.close();
            globalResults.close();
        } catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(Spheroid_Expansion.class.getName()).log(Level.SEVERE, null, ex);
        }
        tools.print("All done!");
    }
}
