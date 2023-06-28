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
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import org.apache.commons.io.FilenameUtils;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import mcib3d.geom.Point3D;
import mcib3d.geom2.measurements.MeasureCentroid;


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
    public double shollDistStart;
    public double shollDistEnd;
    public double spheroidRad;
    public double spheroidArea;
    public double phalloidinArea;
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
        cal.pixelDepth = 1;
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
   public Objects3DIntPopulation stardistDetection(ImagePlus img, Roi roiSpheroid) throws IOException{
       ImagePlus imgNucMasked = new Duplicator().run(img);       
       // mask nucleus image with spheroid circle
       imgNucMasked.setRoi(roiSpheroid);
       IJ.run(imgNucMasked, "Clear", "slice");
       imgNucMasked.deleteRoi();
        // StarDist
       File starDistModelFile = new File(modelsPath+File.separator+stardistModel);
       StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
       star.loadInput(imgNucMasked);
       star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlayThresh, stardistOutput);
       star.run();
       ImagePlus imgLabels = star.getLabelImagePlus().duplicate();
       imgLabels.setCalibration(cal); 
       closeImages(imgNucMasked);
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
    public Roi findSpheroid( ImagePlus imgNuc, ImagePlus spheroidImg) {
        // Merge channel (multiply)
        ImagePlus imgNucDup = new Duplicator().run(imgNuc);
        ImageCalculator multi = new ImageCalculator();
        ImagePlus spheroidMask = multi.run("add", imgNucDup, spheroidImg);
        IJ.run(spheroidMask, "Median...", "radius=6");
        IJ.setAutoThreshold(spheroidMask, "Li dark no-reset");
        Prefs.blackBackground = false;
        IJ.run(spheroidMask, "Convert to Mask", "");
        spheroidMask.setCalibration(cal);
        Roi roiSpheroid = computeCenterRadius(spheroidMask);
        spheroidMask.setRoi(roiSpheroid);
        IJ.setForegroundColor(0, 0, 0);
        IJ.setBackgroundColor(255, 255, 255);
        IJ.run(spheroidMask, "Clear Outside", "");
        IJ.setAutoThreshold(spheroidMask, "Default no-reset");
        ResultsTable results = new ResultsTable();
        Analyzer ana = new Analyzer(spheroidMask, Measurements.AREA+Measurements.LIMIT, results);
        ana.measure();
        spheroidArea = results.getValue("Area", 0);
        System.out.println("Spheroid area="+spheroidArea);
        results.reset();
        closeImages(imgNucDup);
        closeImages(spheroidMask);
        return(roiSpheroid);
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
     * Compute phalloidin area in sholl circle
     */
    public void phalloidinShollAnalysis(ImagePlus img, ResultsTable results) {
        ResultsTable resultsTemp = new ResultsTable();
        int row = 0;
        IJ.setForegroundColor(255, 255, 255);
        IJ.setBackgroundColor(255, 255, 255);
        double start = shollDistStart;
        double end = shollDistEnd / cal.pixelWidth;
        double step = shollStep /cal.pixelWidth;
        for (double i = start; i <= end; i+=step) {
            Roi circleInt = new OvalRoi(shollCenterX - i, shollCenterY - i, i*2, i*2);
            Roi circleOut = new OvalRoi(shollCenterX - (i+step), shollCenterY - (i+step), (i+step)*2, (i+step)*2);
            ImagePlus imgMask = new Duplicator().run(img);
            imgMask.setRoi(circleInt);
            shollOverlay.add(circleInt);
            IJ.run(imgMask, "Fill", "slice");
            imgMask.setRoi(circleOut);
            shollOverlay.add(circleOut);
            IJ.run(imgMask, "Clear Outside", "");
            IJ.setAutoThreshold(imgMask, "Default no-reset");
            imgMask.deleteRoi();
            resultsTemp.reset();
            Analyzer ana = new Analyzer(imgMask, Measurements.AREA+Measurements.LIMIT, resultsTemp);
            ana.measure();
            double area = resultsTemp.getValue("Area", 0);
            results.setValue("Phalloid Area", row, area);
            row++;
            closeImages(imgMask);
        }
    }
    
    public ImagePlus phalloidinMask(ImagePlus imgSpheroid) {
        // create mask for phalloidin
        ImagePlus mask = new Duplicator().run(imgSpheroid);
        IJ.run(mask, "Median...", "radius=4");
        IJ.setAutoThreshold(mask, spheroidThMethod+" dark no-reset");
        Prefs.blackBackground = false;
        IJ.run(mask, "Convert to Mask", "");
        IJ.run(mask, "Fill Holes", "");
        mask.setCalibration(cal);
        return(mask);
    }
    
    /**
     * 
     */
    public ResultsTable computeSholl(Roi roiSpheroid, ArrayList<Double> nucDist, ImagePlus imgMask) {
        IJ.setAutoThreshold(imgMask, "Default no-reset");
        ResultsTable shollResults = new ResultsTable();
        Analyzer ana = new Analyzer(imgMask, Measurements.AREA+Measurements.LIMIT, shollResults);
        ana.measure();
        phalloidinArea = shollResults.getValue("Area", 0);
        shollResults.reset();
        //System.out.println(phalloidinArea);
        double vxWH = Math.sqrt(cal.pixelWidth * cal.pixelHeight);
        int wdth = imgMask.getWidth();
        double hght =  imgMask.getHeight();
        double dx = (shollCenterX <= wdth / 2) ? (shollCenterX - wdth) * vxWH : shollCenterX * vxWH;
        double dy = (shollCenterY <= hght / 2) ? (shollCenterY - hght) * vxWH : shollCenterY * vxWH;
        double dz = 1;
        // start & end in pixels
        shollDistEnd = Math.sqrt(dx * dx + dy * dy + dz * dz);
       
        int row = 0;
        System.out.println("Nucleus Sholl analysis distance start = "+spheroidRad+" distance stop = "+shollDistEnd+" ...");
        for (double i = spheroidRad; i <= shollDistEnd; i+=shollStep) {
            int nucNb = 0;
            for (Double dist : nucDist) {
                if (dist >= i && dist < (i+shollStep))
                    nucNb++;
            }
            shollResults.setValue("Radius", row, i);
            shollResults.setValue("Nucleus", row, nucNb);
            row++;
        } 
        shollDistStart = spheroidRad / cal.pixelWidth;
        System.out.println("Phalloidin Sholl analysis area start = "+spheroidRad+" distance stop = "+shollDistEnd+" ...");
        phalloidinShollAnalysis(imgMask, shollResults);
        return(shollResults);
    }
    
    /**
     * Compute parameters
     * find nucleus distance to spheroid center
     */
    public ArrayList<Double> computeNucleusDistance(Objects3DIntPopulation nucPop, ImagePlus imgSpheroid) {
        
        ArrayList<Double> nucDist = new ArrayList<>();
        Point3D spheroidCenter = new Point3D(shollCenterX, shollCenterY, 1);
        for (Object3DInt nucObj : nucPop.getObjects3DInt()) {
            Point3D nucCenter = new MeasureCentroid(nucObj).getCentroidAsPoint();
            nucDist.add(nucCenter.distance(spheroidCenter)*cal.pixelWidth);
        }
        return(nucDist);
    }
    
    /**
     * Compute spheroid diameter
     */
    public Roi computeCenterRadius(ImagePlus img) {
        IJ.run(img, "Create Selection", "");
        IJ.run(img, "Fit Circle", "");
        Roi roiSpheroid = img.getRoi();
        spheroidRad = roiSpheroid.getFeretsDiameter()/2;
        shollCenterX = roiSpheroid.getContourCentroid()[0];
        shollCenterY = roiSpheroid.getContourCentroid()[1];
        return(roiSpheroid);
    }
    
    /**
     * Save results
     */
    public void saveResults(Objects3DIntPopulation nucPop, String imgName, String seriesName, ArrayList<Double> diams,
            BufferedWriter outPutResults) throws IOException {
        int index = 0;
        for (Object3DInt nucObj : nucPop.getObjects3DInt()) {
            double nucVol = new MeasureVolume(nucObj).getVolumeUnit();
            outPutResults.write(imgName+"\t"+seriesName+"\t"+phalloidinArea+"\t"+spheroidArea+"\t"+spheroidRad+"\t"+nucObj.getLabel()+"\t"+nucVol+"\t"+diams.get(index)+"\n");
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
    public void saveObjectsImage (ImagePlus img, Objects3DIntPopulation nucPop, ImagePlus imgPhalloidinMask, String imgName, String outDirResults) {
        // green spheroid, red nuc
        ImageHandler imgNuc = ImageHandler.wrap(img).createSameDimensions();
        // draw nucleus population
        nucPop.drawInImage(imgNuc);
        
        ImagePlus[] imgColors = {imgNuc.getImagePlus(), imgPhalloidinMask, null, img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
        imgObjects.setOverlay(shollOverlay);
        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + imgName + "_Objects.tif");
        imgNuc.closeImagePlus();
    }
    
    
}
