package Spheroid_Expansion_Tools;

import Spheroid_Expansion_Tools.StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
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
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import org.apache.commons.io.FilenameUtils;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import mcib3d.geom.Point3D;
import mcib3d.geom2.measurements.MeasureCentroid;
import inra.ijpb.binary.BinaryImages;
import java.util.HashMap;


/**
 * @author ORION-CIRB
 */
public class Tools {
    
    private final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    private final String urlHelp = "https://github.com/orion-cirb/Spheroid_Expansion.git";
    
    public String[] channelNames = {"DAPI", "Phalloidin"};
    public Calibration cal;
    public double pixArea;
    
    // Nuclei detection with Stardist
    public File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    protected String stardistModel = "StandardFluo.zip";
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistOverlapThresh = 0.25;
    private double stardistProbThresh = 0.75;
    public double minNucVol = 30;
    public double maxNucVol = 300;
    
    private String spheroidThMethod = "Li";
    private String phalloidinThMethod = "Triangle";
    private int shollStep = 30;


    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    

    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clijx.CLIJx");
        } catch (ClassNotFoundException e) {
            IJ.showMessage("Error", "CLIJx not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom2.Object3DInt");
        } catch (ClassNotFoundException e) {
            IJ.showMessage("Error", "3D ImageJ Suite not installed, please install it from update site");
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
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal = new Calibration();
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
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels(String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
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
        return(channels.toArray(new String[channels.size()]));         
    }
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        
        String[] thMethods = AutoThresholder.getMethods();
        gd.addMessage("Spheroid fitting", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Thresholding method: ", thMethods, spheroidThMethod);
        
        gd.addMessage("Phalloidin segmentation", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Thresholding method: ", thMethods, phalloidinThMethod);
        
        gd.addMessage("Nuclei detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min nucleus area (µm2): ", minNucVol);
        gd.addNumericField("Max nucleus area (µm2): ", maxNucVol);
        
        gd.addMessage("Sholl analysis", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Circles interval (µm): ", shollStep);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel size (µm): ", cal.pixelDepth);
        gd.addHelp(urlHelp);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        
        spheroidThMethod = gd.getNextChoice();
        phalloidinThMethod = gd.getNextChoice();
        minNucVol = gd.getNextNumber();
        maxNucVol = gd.getNextNumber();
        shollStep = (int)gd.getNextNumber();
        
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = 1;
        pixArea = cal.pixelWidth*cal.pixelWidth;
        
        if (gd.wasCanceled())
            chChoices = null;  
        return(chChoices);
    }
    
    
    /**
     * Flush and close an image
     */
    public void flushCloseImg(ImagePlus img) {
        img.flush();
        img.close();
    }

    
    /**
     * Do z-projection
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
     * Find spheroid
     */
    public Roi fitSpheroid(ImagePlus imgNuc, ImagePlus imgSpheroid) {
        // Add channels
        ImagePlus imgMask = new ImageCalculator().run("add create", imgNuc, imgSpheroid);
        // Median filter
        IJ.run(imgMask, "Median...", "radius=6");
        // Threshold
        IJ.setAutoThreshold(imgMask, spheroidThMethod+" dark");
        IJ.run(imgMask, "Convert to Mask", "");
        imgMask.setCalibration(cal);
        // Fit circle
        ImagePlus spheroidMask = BinaryImages.keepLargestRegion​(imgMask);
        IJ.run(spheroidMask, "Create Selection", "");
        IJ.run(spheroidMask, "Fit Circle", "");
        Roi spheroidRoi = spheroidMask.getRoi();

        flushCloseImg(imgMask);
        flushCloseImg(spheroidMask);
        return(spheroidRoi);
    }
    
    
    /**
     * Compute parameters of spheroid-fitted circle
     */
    public HashMap<String, Double> computeSpheroidParams(Roi roiSpheroid) {
        HashMap<String, Double> params = new HashMap<>();
        params.put("radius", roiSpheroid.getFeretsDiameter()/2);
        params.put("centerX", roiSpheroid.getContourCentroid()[0]);
        params.put("centerY", roiSpheroid.getContourCentroid()[1]);
                    
        System.out.println("Spheroid radius = "+params.get("radius")*cal.pixelWidth);
        System.out.println("Spheroid center = ("+params.get("centerX")*cal.pixelWidth+", "+params.get("centerY")*cal.pixelHeight+")");
        return(params);
    }
    
    
    /**
     * Get phalloidin mask
     */
    public ImagePlus phalloidinMask(ImagePlus imgSpheroid) {
        ImagePlus mask = new Duplicator().run(imgSpheroid);
        IJ.run(mask, "Subtract Background...", "rolling=300 sliding");
        IJ.run(mask, "Median...", "radius=4");
        IJ.setAutoThreshold(mask, phalloidinThMethod+" dark");
        IJ.run(mask, "Convert to Mask", "");
        mask.setCalibration(cal);
        return(mask);
    }
    
    
    /**
     * Detect nuclei with StarDist 2D
     */
   public Objects3DIntPopulation stardistDetection(ImagePlus imgNuc, Roi roiSpheroid) throws IOException {
        // StarDist
        ImagePlus imgNucDup = new Duplicator().run(imgNuc);
        File starDistModelFile = new File(modelsPath+File.separator+stardistModel);
        StarDist2D star = new StarDist2D(new Object(), starDistModelFile);
        star.loadInput(imgNucDup);
        star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlapThresh, "Label Image");
        star.run();

        // Fill spheroid ROI with black in nuclei image
        ImagePlus imgLabels = star.getLabelImagePlus();
        imgLabels.setCalibration(cal);
        imgLabels.setRoi(roiSpheroid);
        IJ.setBackgroundColor(0, 0, 0);
        IJ.run(imgLabels, "Clear", "");
        imgLabels.deleteRoi();

        // Get detections as population of objects and filter them by size
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels.duplicate()));  
        System.out.println(pop.getNbObjects()+" nuclei detected");
        popFilterSize(pop, minNucVol, maxNucVol);
        System.out.println(pop.getNbObjects()+ " nuclei remaining after size filtering");

        flushCloseImg(imgNucDup);
        flushCloseImg(imgLabels);
        return(pop);
    }
   
   
    /**
     * Remove from population objects with size < min and size > max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
    
    
    /**
     * Save sholl analysis of nuclei number and phalloidin area in results file
     */
    public Overlay writeShollResults(BufferedWriter results, Roi roi, HashMap<String, Double> roiParams, ImagePlus mask, 
            Objects3DIntPopulation nucPop, String imgName) throws IOException {      
        // Compute nuclei distance to spheroid centroid
        ArrayList<Double> nucleiDists = computeNucleiDistances(nucPop, roiParams);
        
        // Calculate radius of first and last concentric circles (in pixels)
        double vxWH = Math.sqrt(pixArea);
        double dx = (roiParams.get("centerX") <= 0.5*mask.getWidth()) ? (roiParams.get("centerX")-mask.getWidth())*vxWH : roiParams.get("centerX")*vxWH;
        double dy = (roiParams.get("centerY") <= 0.5*mask.getHeight()) ? (roiParams.get("centerY")-mask.getHeight())*vxWH : roiParams.get("centerY")*vxWH;
        double stop = Math.sqrt(dx*dx + dy*dy) / cal.pixelWidth;
        double step = shollStep / cal.pixelWidth;
        double start = roiParams.get("radius");
       
        IJ.setBackgroundColor(255, 255, 255);
        int circleID = 1;
        Overlay shollOverlay = new Overlay();
        for (double i = start; i < (stop-step); i += step) {
            // Number of nuclei
            int nucleiNb = 0;
            for (Double dist: nucleiDists) {
                if (dist >= i && dist < (i+step))
                    nucleiNb++;
            }
            
            // Phalloidin area
            Roi circleInt = new OvalRoi(roiParams.get("centerX")-i, roiParams.get("centerY")-i, i*2, i*2);
            Roi circleOut = new OvalRoi(roiParams.get("centerX")-(i+step), roiParams.get("centerY")-(i+step), (i+step)*2, (i+step)*2);
            double phalloidinArea = getPhalloidinArea(mask, circleInt, circleOut);
            shollOverlay.add(circleInt);
            shollOverlay.add(circleOut);
           
            // Save results
            results.write(imgName+"\t"+circleID+"\t"+i*cal.pixelWidth+"\t"+phalloidinArea+"\t"+nucleiNb+"\n");
            results.flush();
            circleID++;
        }
        return(shollOverlay);
    }
    
    
    /**
     * Compute nuclei distances to spheroid centroid
     */
    public ArrayList<Double> computeNucleiDistances(Objects3DIntPopulation nucPop, HashMap<String, Double> spheroidParams) {
        ArrayList<Double> nucDist = new ArrayList<>();
        Point3D spheroidCenter = new Point3D(spheroidParams.get("centerX"), spheroidParams.get("centerY"), 1);
        for (Object3DInt nucObj: nucPop.getObjects3DInt()) {
            Point3D nucCenter = new MeasureCentroid(nucObj).getCentroidAsPoint();
            nucDist.add(nucCenter.distance(spheroidCenter));
        }
        return(nucDist);
    }
    
    
    /**
     * Compute nuclei distances to spheroid centroid
     */
    public double getPhalloidinArea(ImagePlus mask, Roi circleInt, Roi circleOut) {
        ImagePlus maskDup = new Duplicator().run(mask);
        maskDup.setRoi(circleInt);
        IJ.run(maskDup, "Clear", "");
        maskDup.setRoi(circleOut);
        IJ.run(maskDup, "Clear Outside", "");
        maskDup.deleteRoi();
        IJ.setAutoThreshold(maskDup, "Default");
        ResultsTable resultsTemp = new ResultsTable();
        Analyzer ana = new Analyzer(maskDup, Measurements.AREA+Measurements.LIMIT, resultsTemp);
        ana.measure();
        return(resultsTemp.getValue("Area", 0));
    }

    
    /**
     * Save global results in results
     */
    public void writeGlobalResults(BufferedWriter results, Roi roi, HashMap<String, Double> roiParams,
            ImagePlus mask, Objects3DIntPopulation nucPop, String imgName) throws IOException {
        // Spheroid area
        IJ.setBackgroundColor(255, 255, 255);
        ImagePlus maskDup = new Duplicator().run(mask);
        maskDup.setRoi(roi);
        IJ.run(maskDup, "Clear Outside", "");
        IJ.setAutoThreshold(maskDup, "Default");
        ResultsTable resultsTemp = new ResultsTable();
        Analyzer ana = new Analyzer(maskDup, Measurements.AREA+Measurements.LIMIT, resultsTemp);
        ana.measure();
        double spheroidArea = resultsTemp.getValue("Area", 0);
        
        // Phalloidin area
        IJ.setAutoThreshold(mask, "Default ");
        resultsTemp.reset();
        ana = new Analyzer(mask, Measurements.AREA+Measurements.LIMIT, resultsTemp);
        ana.measure();
        double phalloidinArea = resultsTemp.getValue("Area", 0);
        
        results.write(imgName+"\t"+spheroidArea+"\t"+roiParams.get("radius")*cal.pixelWidth+"\t"+phalloidinArea+"\t"+nucPop.getNbObjects()+"\n");
        results.flush();
    }
    

    /**
     * Draw results
     */
    public void drawResults(ImagePlus img, Objects3DIntPopulation nucPop, ImagePlus phalloidinMask, Overlay shollOverlay,
            String outDirResults, String imgName) {
        // Draw phalloidin mask in green, nuclei in red, sholl overlay in yellow
        ImageHandler imgNuc = ImageHandler.wrap(img).createSameDimensions();
        nucPop.drawInImage(imgNuc);
        ImagePlus[] imgColors = {imgNuc.getImagePlus(), phalloidinMask, null, img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setOverlay(shollOverlay);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
        
        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults+imgName+".tif");
        
        imgNuc.closeImagePlus();
        flushCloseImg(imgObjects);
    }
}
