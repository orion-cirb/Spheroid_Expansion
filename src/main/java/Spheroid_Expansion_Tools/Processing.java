package Spheroid_Expansion_Tools;

import Spheroid_Expansion_Tools.StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.process.AutoThresholder;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.Measure2Distance;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.snt.analysis.sholl.*;
import sc.fiji.snt.analysis.sholl.gui.*;
import sc.fiji.snt.analysis.sholl.math.*;
import sc.fiji.snt.analysis.sholl.parsers.*;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;


/**
 *
 * @author phm
 */

public class Processing {
    
     // Stardist
    public Object syncObject = new Object();
    public File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    protected String stardistModel = "StandardFluo.zip";
    public String stardistOutput = "Label Image"; 
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private double stardistProbThresh = 0.6;
    private final double stardistOverlayThresh = 0.25;
    
    private String[] thMethods = AutoThresholder.getMethods();
    private String spheroidThMethod = "Huang";
    private int shollStep = 30;
    public double shollCenterX;
    public double shollCenterY;
    public double spheroidRad;
    public Overlay shollOverlay = new Overlay();
    public double minNucVol = 50, maxNuclVol = 500;
    public double minSpheroidVol = 100;
    public Calibration cal = new Calibration();
    private double pixVol = 0;
    private String urlHelp = "https://github.com/orion-cirb/Spheroid_Expansion.git";
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("mcib3d.geom");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    /**
     * Find images extension
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
               case "nd" :
                   ext = fileExt;
                   break;
                 case "nd2" :
                   ext = fileExt;
                   break;
                 case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "lsm" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }
    
   /**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
     /**
     * Find image calibration
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        ArrayList<String> channels = new ArrayList<>();
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;    
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelFluor(0, n).toString());
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break; 
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break;        
            default :
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));

        }
        channels.add("None");
        return(channels.toArray(new String[channels.size()]));         
    }
    
    
     /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) { 
        String[] channelNames = {"Nucleus", "Spheroid"};
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        gd.addMessage("Spheroid detection", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Threshold method : ",thMethods, spheroidThMethod);
        gd.addMessage("Sholl analysis", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Circle step interval (µm): ", shollStep);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel size (µm): ", cal.pixelDepth);
        gd.addHelp(urlHelp);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        if (gd.wasCanceled())
            chChoices = null;        
        spheroidThMethod = gd.getNextChoice();
        shollStep = (int)gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        return(chChoices);
    }
    
    
    /**
     *
     * @param img
     */
    public void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }

    
  /**
     * return objects population in an binary image
     * @param img
     * @return pop
     */

    private Objects3DIntPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
        return pop;
    } 
    
    /**
     * Apply StarDist 2D slice by slice
     * Label detections in 3D
     */
   public Objects3DIntPopulation stardistDetection(ImagePlus img) throws IOException{
       // mask nucleus image with spheroid circle
       double X = shollCenterX - spheroidRad/cal.pixelWidth;
       double Y = shollCenterY - spheroidRad/cal.pixelHeight;
       double sX = (spheroidRad/cal.pixelWidth)*2;
       double sY = (spheroidRad/cal.pixelHeight)*2;
       OvalRoi spheroidCircle = new OvalRoi(X, Y, sX, sY);
       img.setRoi(spheroidCircle);
       IJ.run(img, "Clear", "slice");
       img.deleteRoi();
        // StarDist
       File starDistModelFile = new File(modelsPath+File.separator+stardistModel);
       StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
       star.loadInput(img);
       star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlayThresh, stardistOutput);
       star.run();
       ImagePlus imgLabels = star.getLabelImagePlus().duplicate();
       imgLabels.setCalibration(cal);               
       // Get objects as a population of objects
       Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));  
       System.out.println(pop.getNbObjects()+" Stardist detections");
       // Filter objects
       popFilterSize(pop, minNucVol, maxNuclVol);
       System.out.println(pop.getNbObjects()+ " detections remaining after size filtering");
       closeImages(imgLabels);
       return(pop);
    }
   
   
      
    /**
     * Find Spheroid Object
     */
    public Object3DInt findSpheroid( ImagePlus imgNuc, ImagePlus spheroidImg) {
        ImagePlus imgDup = new Duplicator().run(imgNuc);        
        // Merge channel (multiply)
        ImageCalculator multi = new ImageCalculator();
        multi.calculate("multiply", imgDup, spheroidImg);
        IJ.run(imgDup, "Median...", "radius=6");
        IJ.setAutoThreshold(imgDup, "Li dark no-reset");
        Prefs.blackBackground = false;
        IJ.run(imgDup, "Convert to Mask", "");
        imgDup.setCalibration(cal);
        Objects3DIntPopulation pop = getPopFromImage(imgDup);
        popFilterSize(pop, minSpheroidVol, Double.MAX_VALUE);
        // get spheroid as on object
        ImageHandler imh = ImageHandler.wrap(imgDup).createSameDimensions();
        for (Object3DInt obj : pop.getObjects3DInt())
                obj.drawObject(imh, 255);
        Object3DInt spheroidObj = new Object3DInt(imh);
        closeImages(imgDup);
        imh.closeImagePlus();
        spheroidObj.setVoxelSizeXY(cal.pixelWidth);
        spheroidObj.setVoxelSizeZ(cal.pixelDepth);
        return(spheroidObj);
    }
    

    /**
     * Remove object with size < min and size > max
     * @param pop
     * @param min
     * @param max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
    
    
     /**
     * Do Z projection
     * @param img
     * @param param
     * @return 
     */
    public ImagePlus doZProjection(ImagePlus img, int param) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(param);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
    
    /**
     * Sholl Analysis
     */
    public ResultsTable shollAnalysis(ImagePlus img) {
        ImagePlus imgMask = new Duplicator().run(img);
        // threshold image
        IJ.run(imgMask, "Gaussian Blur...", "sigma=2");
        IJ.setAutoThreshold(imgMask, spheroidThMethod+" dark no-reset");
        Prefs.blackBackground = false;
        IJ.run(imgMask, "Convert to Mask", "");
        IJ.run(imgMask, "Median...", "radius=8");
        imgMask.setCalibration(cal);
        ImageParser2D parser = new ImageParser2D(imgMask);
        // 5 samples/radius w/ median integration
        parser.setRadiiSpan(5, ImageParser2D.MEAN);
        parser.setCenter(shollCenterX*cal.pixelWidth, shollCenterY*cal.pixelHeight, 1);
        parser.setRadii(spheroidRad, shollStep, parser.maxPossibleRadius());
        System.out.println("Sholl analysis in progress ...");
        parser.parse();
        //closeImages(imgMask);
        if (!parser.successful()) {
            System.out.println("Spheroid Sholl analysis " + img.getTitle() + " could not be analyzed!!!");
            return(null);
        }
        else {
            Profile profile = parser.getProfile();
            if (profile.isEmpty()) {
                System.out.println("All intersection counts were zero! Invalid threshold range!?");
                return(null);
            }
            profile.setSpatialCalibration(cal);
            shollOverlay = profile.getROIs();
            shollOverlay.setStrokeColor(Color.yellow);
            Plot plot = new ShollPlot(new LinearProfileStats(profile));
            ResultsTable resultsTable = plot.getResultsTable();
            resultsTable.renameColumn("X", "Distance (µm)");
            resultsTable.renameColumn("Y", "Spheroid intersections");
            closeImages(imgMask);
            return(resultsTable);
        }
    }
    
    /**
     * 
     */
    public void computeShollNucleus(ArrayList<Double> nucDist, ResultsTable shollResults, String outDirResults, String imgName) {
        double distStart = shollResults.getValue("Distance (µm)", 0);
        double distEnd = shollResults.getValue("Distance (µm)", shollResults.size()-1);
        int row = 0;
        System.out.println("Nucleus Sholl analysis distance start = "+distStart+" distance stop = "+distEnd+" ...");
        for (double i = distStart; i <= distEnd; i+=shollStep) {
            int nucNb = 0;
            for (Double dist : nucDist) {
                if (dist >= i && dist < (i+shollStep))
                    nucNb++;
            }
            shollResults.setValue("Nucleus", row, nucNb);
            row++;
        }
        shollResults.save(outDirResults+imgName+".xls");
    }
    
    /**
     * Compute parameters
     * 
     */
    public ArrayList<Double> computeParameters(Object3DInt spheroidObj, Objects3DIntPopulation nucPop) {
        ArrayList<Double> nucDist = new ArrayList<>();
        for (Object3DInt nucObj : nucPop.getObjects3DInt()) 
            nucDist.add(new Measure2Distance(nucObj, spheroidObj).getValue(Measure2Distance.DIST_CC_UNIT));
        return(nucDist);
    }
    
    /**
     * Compute spheroid diameter
     */
    public void computeCenterRadius(ImagePlus imgSpheroid, Object3DInt spheroidObj) {
        ImageHandler imh = ImageHandler.wrap(imgSpheroid).createSameDimensions();
        spheroidObj.drawObject(imh, 255);
        ImagePlus img = imh.getImagePlus();
        IJ.setAutoThreshold(img, "Default dark");
        IJ.run(img, "Create Selection", "");
        IJ.run(img, "Fit Circle", "");
        Roi roiSpheroid = img.getRoi();
        closeImages(img);
        spheroidRad = (roiSpheroid.getFeretsDiameter()*cal.pixelWidth)/2;
        shollCenterX = roiSpheroid.getContourCentroid()[0];
        shollCenterY = roiSpheroid.getContourCentroid()[1];
    }
    
    /**
     * Save results
     */
    public void saveResults(Objects3DIntPopulation nucPop, Object3DInt spheroidObj, String imgName, String seriesName, ArrayList<Double> diams,
            BufferedWriter outPutResults) throws IOException {
        double spheroidArea = new MeasureVolume(spheroidObj).getVolumeUnit();
        int index = 0;
        for (Object3DInt nucObj : nucPop.getObjects3DInt()) {
            double nucVol = new MeasureVolume(nucObj).getVolumeUnit();
            outPutResults.write(imgName+"\t"+seriesName+"\t"+spheroidArea+"\t"+spheroidRad+"\t"+nucObj.getLabel()+"\t"+nucVol+"\t"+diams.get(index)+"\n");
            index++;

        }
    }
    
    /**
    /**
     * save images objects population
     * @param img
     * @param nucPop
     * @param spheroidObj
     * @param imgname
     */
    public void saveObjectsImage (ImagePlus img, Objects3DIntPopulation nucPop, Object3DInt spheroidObj, String imgName, String outDirResults) {
        // green spheroid, red nuc
        ImageHandler imgNuc = ImageHandler.wrap(img).createSameDimensions();
        ImageHandler imgSpheroid = ImageHandler.wrap(img).createSameDimensions();
        // draw nucleus population
        nucPop.drawInImage(imgNuc);
        // draw spheroid
        spheroidObj.drawObject(imgSpheroid, 255);
        ImagePlus[] imgColors = {imgNuc.getImagePlus(), imgSpheroid.getImagePlus(), null, img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
        imgObjects.setOverlay(shollOverlay);
        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + imgName + "_Objects.tif");
        imgNuc.closeImagePlus();
        imgSpheroid.closeImagePlus();
    }
    
    
}
