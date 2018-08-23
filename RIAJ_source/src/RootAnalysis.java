/**
* @author Guillaume Lobet | Forschungszentrum Jülich - Université catholique de Louvain
*
**/

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.EDM;
import ij.process.BinaryProcessor;
import ij.process.ByteProcessor;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;

public class RootAnalysis {
	
	// Export parameters
	static File dirAll, dirParam;//, dirConvex;	
	static File[] images; 		
	static String  csvParamFolder, csvParamAnalysis, imName, baseName, fullName, tpsFolder, efdFolder, shapeFolder;
	
	// Analysis parameters
	static int nROI, nEFD, nCoord = 10, counter, nSlices = 30;
	static float scalePix, scaleCm, scale, rootMinSize;
	static final float epsilon = 1e-9f;
	static boolean blackRoots, saveImages, saveTips, verbatim, saveTPS, saveEFD, saveShapes;

	// Estimators
	static float area, depth, length, width, comX, comY, Ymid, Xmid, bX, bY;
	static float[] params = new float[23+(nSlices*2)+(nCoord*4)];
    static float[] xCoord;
    static float[] yCoord;
    static float[] diffCoord;
    static float[] cumulCoord;
    
    // Tools
	static ResultsTable rt = new ResultsTable();
	static PrintWriter pwParam, pwTPS, pwEFD, pwAnalysis;	
	static ParticleAnalyzer pa;	
	static Analyzer an;
	
	/**
	 * Constructor
	 * @param f = File containing the different images
	 * @param file = where to save csv file
	 * @param scaleP = scale, in pixels
	 * @param scaleC = scale, in cm
	 */
	public RootAnalysis(File f,
			String file,
			float scaleP,
			float scaleC,
			boolean black,
			float minsize,
			boolean verb,
			boolean save,
			boolean saveT,
			boolean tps,
			boolean efd,
			boolean shapes,
			String dirS
			){
		
		// Set up the different variables
		scalePix = scaleP;
		scaleCm = scaleC;
		dirAll = f;
		csvParamFolder = file;
		csvParamAnalysis = file.substring(0, file.length()-4)+"-analysis.csv";
		tpsFolder = file.substring(0, file.length()-4)+"-shape.tps";
		efdFolder = file.substring(0, file.length()-4)+"-efd.csv";
		saveTPS = tps;
		saveEFD = efd;
		saveShapes = shapes;
		shapeFolder = dirS;
		
		blackRoots = black;
		rootMinSize = minsize;
		saveImages = save;
		saveTips = saveT;
		verbatim = verb;
		
	    xCoord = new float[nCoord * 2];
	    yCoord = new float[nCoord * 2];
	    diffCoord = new float[nCoord];
	    cumulCoord = new float[nCoord];
	    
	    nEFD = 10;
	    
		// Analyze the plants
		analyze();
	}


	/**
	 * Perform the analysis of all the images
	 */
	public void analyze(){
		
		ImagePlus nextImage = null;
		
        // Get all the images files in the directory
		images = dirAll.listFiles();
		for(int i = 0; i< images.length; i++) if(images[i].isHidden()) images[i].delete();
		images = dirAll.listFiles(new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".tiff") || 
		        		name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".jpeg") ||
		        		 name.toLowerCase().endsWith(".png");
		    }
		});

		// Counter for the processing time
		IJ.log("Root image analysis started: "+dirAll.getAbsolutePath().toString());
		long startD = System.currentTimeMillis(); 
		int counter = 0;	

		// Initialize CSV
		pwParam = Util.initializeCSV(csvParamFolder);
		pwAnalysis = Util.initializeCSV(csvParamAnalysis);
		printParamCSVHeader();		
		printAnalysisCSVHeader();		
		
		if(saveTPS) pwTPS = Util.initializeCSV(tpsFolder);
		if(saveEFD) pwEFD = Util.initializeCSV(efdFolder);
		if(saveEFD) printEFDCSVHeader();		

		
		// Create the folder structure to store the images			
		if(saveImages || saveShapes){
			File dirSave; 
			dirSave = Util.createFolderStructure(dirAll.getAbsolutePath(), false, false, false, true);
			dirParam = new File(dirSave.getAbsolutePath()+"/param/");
		}
		
		// Navigate the different images in the time serie
		int percent = 0;
	    float progression = 0;
		for(int i = 0; i < images.length; i++){
			
			long startD1 = System.currentTimeMillis();

			// Open the image
			nextImage = IJ.openImage(images[i].getAbsolutePath());
			
			progression = (i/images.length)*100;
  		  	if(progression > percent){    			
  			  IJ.log(percent+" % of the rsml files converted. "+(images.length-i)+" files remaining.");
  			  System.out.println(percent+" % of the rsml files converted. "+(images.length-i)+" files remaining.");
  			  percent = percent + 5;
  		  	}
	    	
			// Reset the ROI to the size of the image. This is done to prevent previously drawn ROI (ImageJ keep them in memory) to empede the analysis
			nextImage.setRoi(0, 0, nextImage.getWidth(), nextImage.getHeight());

			// Process the name to retrieve the experiment information
			baseName = images[i].getName();
			fullName = images[i].getAbsolutePath();
			
			// Measure the image	
			try{
				getDescriptors(nextImage, i);
				sendAnalysisToCSV(nextImage.getTitle(), nextImage.getWidth(), nextImage.getHeight(), startD1, System.currentTimeMillis());
			}catch(Exception e){
				System.out.println(e);
			}
			// Close the current image
			nextImage.flush(); nextImage.close(); 
			counter ++;
			
			IJ.log("Loading the image "+(i+1)+"/"+images.length+" in "+(startD1 - System.currentTimeMillis()));

		}
		
		// Compute the time taken for the analysis
		long endD = System.currentTimeMillis();
		IJ.log("------------------------");
		IJ.log(counter+" images analyzed in "+(endD - startD)+" ms");		
	}

	
	private void getDescriptors(ImagePlus current, int count){
				
		// Initalisation of the image
		
		ImagePlus currentImage = current.duplicate();		
		
		// Pre-process the image
		if(verbatim) IJ.log("Pre-processing the image");
		ImageProcessor ip = currentImage.getProcessor();
		
		
		// Resize the image to speed up the analysis (resize to width = 800)		
		scale = scalePix / scaleCm;
		float widthCm = ip.getWidth() / scale;
//		int maxScale = 1000;
//		if(ip.getWidth() > maxScale){
//			float h = ip.getHeight();
//			float w = ip.getWidth();
//			ip = ip.resize(maxScale, (int) ((h / w) * maxScale));
//			scale = maxScale / widthCm;
//		}
		
		// Convert to 8bit image if needed
		if(ip.getBitDepth() != 8) ip = ip.convertToByte(true);

		// If the root is white on black, then invert the image
		if(!blackRoots){
			ip.invert();
			System.out.println("root inverted");
		}
		
		// Threshold the image
	    ip.setAutoThreshold("Otsu"); 
	    currentImage.setProcessor(ip);
	    
	    // Remove small particles in the image
		pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_MASKS, Measurements.AREA, rt, rootMinSize, 10e9, 0, 1);
		pa.analyze(currentImage);
		
		// Get the mask from the ParticuleAnalyser
		currentImage = IJ.getImage(); 
		currentImage.hide(); // Hide the mask, we do not want to display it.
		ip = currentImage.getProcessor();	    
	    
	    
	    // Reset calibration 
	    // This is needed in case the image was previously calibrated using ImageJ.
	    // TIFF images store the calibration...
		Calibration calDefault = new Calibration();
	    calDefault.setUnit("px");
		calDefault.pixelHeight = 1;
		calDefault.pixelWidth = 1;
	    currentImage.setCalibration(calDefault);
	    
		//----------------------------------------------------------------------------------------	
	    
	    //Get base coordinate of root system      	
      	IJ.run(currentImage, "Create Selection", "");
        Analyzer.setResultsTable(rt);
        rt.reset();
        an = new Analyzer(currentImage, Measurements.CENTER_OF_MASS | Measurements.RECT, rt);
        an.measure();
        
        Ymid = (float) rt.getValue("BY", 0);
        Xmid = (float) rt.getValue("XM", 0);
                    
        counter = 0;
        
		// Create skeleton
        ImagePlus skelImage = new ImagePlus();
		BinaryProcessor bp = new BinaryProcessor(new ByteProcessor(ip, true));
		//for(int i = 0; i < 5; i++) bp.smooth(); // Smooth the image for a better skeletonisation
		bp.autoThreshold();
		bp.skeletonize();
		//bp.invert();
		skelImage.setProcessor(bp);
		
	    if(saveImages) IJ.save(skelImage, dirParam.getAbsolutePath()+"/"+baseName+"_skeleton.tiff");
	 
		
		skelImage.setRoi(0, 0, ip.getWidth(), ip.getHeight());
		currentImage.setRoi(0, 0, ip.getWidth(), ip.getHeight());
		
		//skelImage.show(); currentImage.show();
		
        //Get area of the root system
        getDiameters(currentImage.duplicate(), skelImage.duplicate());
		        
        getGeometry(currentImage.duplicate(), skelImage.duplicate());
        
        getDensityEllipses(currentImage.duplicate());

        getDensityRectangles(currentImage.duplicate());

        getDirectionality(currentImage.duplicate());
        
//        getWhiteEllipses(currentImage.duplicate());
		
        getPixelsCount(skelImage.duplicate(), currentImage.duplicate());
        
        getPixelProfiles(skelImage.duplicate());

        getConvexHull(currentImage.duplicate(), saveEFD);
     
        getCoordinates(currentImage.duplicate());
        
                
        counter = 0;
        
	    // Save the images for post-processing check
		currentImage.flush(); currentImage.close();
		skelImage.flush(); skelImage.close();
		
	    sendParametersToCSV();
	}
		

	/**
	 * 
	 * @param im
	 */
	private void getDiameters(ImagePlus im, ImagePlus skel){
		
		if(verbatim) IJ.log("Get diameter values");
        
		EDM edm = new EDM();
		ImageCalculator ic = new ImageCalculator();
		
		// Create EDM mask
		ImageProcessor ip = im.getProcessor();
		ip.autoThreshold();;
		//ip.invert();
		
		edm.run(ip);
		im.setProcessor(ip);
		
		// Create EDM Skeleton
		im = ic.run("AND create", im, skel);
		
		IJ.setThreshold(im, 1, 255);
		Analyzer.setResultsTable(rt);
		rt.reset();
		IJ.run(im, "Create Selection", "");
		an = new Analyzer(im, Measurements.MODE | Measurements.MEAN | Measurements.MIN_MAX , rt);
		an.measure();
		
		params[counter++] = (float) rt.getValue("Max", 0) / scale;
		params[counter++] = (float) rt.getValue("Mean", 0) / scale;
		params[counter++] = (float) rt.getValue("Mode", 0) / scale;
		
		im.close();
		skel.close();
	}
	
	/**
	 * 
	 * @param im
	 */
	private void getGeometry(ImagePlus im, ImagePlus skel){
		if(verbatim) IJ.log("- Get geometry");
		
        im.getProcessor().autoThreshold(); 
        IJ.run(im, "Create Selection", "");
        
        Analyzer.setResultsTable(rt);
        rt.reset();     

        an = new Analyzer(skel, Measurements.AREA | Measurements.AREA_FRACTION | Measurements.RECT, rt);
        rt.reset();        
        an.measure();

        // Area
        length = (float) ((rt.getValue("%Area", 0) / 100 )* rt.getValue("Area", 0));
        params[counter++] = length / scale;
        
        
        an = new Analyzer(im, Measurements.AREA | Measurements.CENTER_OF_MASS | Measurements.RECT, rt);
        rt.reset();        
        an.measure();
        
        // Area
        area = (float) rt.getValue("Area", 0);
        params[counter++] = area / scale;
        
        // Width
        width = (float) Math.max(rt.getValue("Width", 0), epsilon);
	    params[counter++] = width / scale;
	    
	    // Height
	    depth = (float) Math.max(rt.getValue("Height", 0), epsilon);
	    params[counter++] = depth / scale;
	    
	    // Ratio
	    params[counter++] = width/depth;
	    
	    //Center of Mass

	    
	    bX = (float) rt.getValue("BX", 0);
	    bY = (float) rt.getValue("BY", 0);

	    comX = (float) (rt.getValue("XM", 0) - bX) / width;
	    params[counter++] = comX;
	    comY  = (float) (rt.getValue("YM", 0) - bY)  / depth;
	    params[counter++] = comY;
	    
	    //Create rectangles overlay
	    if(saveImages){
	     	Roi roi = new Roi((Xmid - 0.5 * width), Ymid, width, depth);
	     	roi.setStrokeColor(Color.blue);
	     	roi.setStrokeWidth(5);
			Overlay overlay = new Overlay(roi); 
			im.setOverlay(overlay); 
			im = im.flatten(); 
			roi = new Roi(rt.getValue("XM", 0)-5, rt.getValue("YM", 0)-5, 10, 10);
	     	roi.setStrokeColor(Color.red);
	     	roi.setStrokeWidth(5);
			overlay = new Overlay(roi); 
			im.setOverlay(overlay); 
			im = im.flatten(); 
	        IJ.save(im, dirParam.getAbsolutePath()+"/"+baseName+"_geometry.tiff");
	    }
	    
	    im.close();
	    skel.close();
	}
	
	/**
	 * 
	 * @param im
	 */
	private void getDensityEllipses(ImagePlus im){
	    
		if(verbatim) IJ.log("-- Calculate density between ellipses");
	    im.getProcessor().autoThreshold();
	    im.getProcessor().invert();
		
	    float[] wMod = {0.125f, 0.25f, 0.375f, 0.5f};
	    float[] dMod = {0.25f, 0.5f, 0.75f, 1f};
	    OvalRoi[] roi = new OvalRoi[dMod.length];
	    float areaPrev = 0;	    
	    Analyzer.setResultsTable(rt);

	    for(int i = 0; i < 4; i++){
		
		    roi[i] = new OvalRoi(Xmid - wMod[i] * width, Ymid, dMod[i] * width, dMod[i] * depth);
		    im.setRoi(roi[i]);	    
		    rt.reset();
		    pa = new ParticleAnalyzer(ParticleAnalyzer.CLEAR_WORKSHEET, Measurements.AREA, rt, 0, 10e9, 0, 1);
			pa.analyze(im);
			float areaSelection = 0;
			for(int j = 0; j < rt.getCounter(); j++){
				areaSelection += (float) rt.getValue("Area", j);			
			}
			float areaProp = (areaSelection-areaPrev) / area;
			params[counter++] = areaProp;
		    areaPrev = areaSelection;
		    
	    }

	    //Create Ellipse overlay
	    if(saveImages){
	     	im.getProcessor().invert();
	     	for(int i = 0; i < roi.length; i++){
	     		roi[i].setStrokeColor(Color.BLUE);
	     		roi[i].setStrokeWidth(4);
		    	Overlay Eloverlay = new Overlay(roi[i]); 
		    	im.setOverlay(Eloverlay); 
		    	im = im.flatten(); 
	     	}	    
	 		IJ.save(im, dirParam.getAbsolutePath()+"/"+baseName+"_ellipses.tiff");
	    }
	    
	    im.close();		
		
	}
	
	/**
	 * 
	 * @param im
	 * @param name
	 */
	private void getDensityRectangles(ImagePlus im){
		
		if(verbatim) IJ.log("--- Calculate density inside rectangles");
		float ar1 = 0;
	    float[] dMod = {0, 0.2f, 0.4f, 0.6f};
	    Roi[] roi = new Roi[dMod.length];
	    Analyzer.setResultsTable(rt);
	    
	    im.getProcessor().autoThreshold();
	    im.getProcessor().invert();
	    
	    for(int i = 0; i < dMod.length; i++){	    	
		    //roi[i] = new Roi((Xmid - 0.25 * width), Ymid + (dMod[i] * depth), 0.5 * width, 0.2 * depth);
		    roi[i] = new Roi((Xmid - 0.5 * width), Ymid + (dMod[i] * depth), width, 0.2 * depth);
		    im.setRoi(roi[i]);      
		    rt.reset();
		    pa = new ParticleAnalyzer(ParticleAnalyzer.CLEAR_WORKSHEET, Measurements.AREA, rt, 0, 10e9, 0, 1);
			pa.analyze(im);
			ar1 = 0;
			for(int j = 0; j < rt.getCounter(); j++){
				ar1 += (float) rt.getValue("Area", j);			
			}
		    params[counter++] = ar1 / area;
	    	
	    }
	    
	    //Create rectangles overlay
	    if(saveImages){
	     	im.getProcessor().invert();
	     	for(int i = 0; i < roi.length; i++){
	     		roi[i].setStrokeColor(Color.blue);
	     		roi[i].setStrokeWidth(5);
			    Overlay overlay = new Overlay(roi[i]); 
			    im.setOverlay(overlay); 
			    im = im.flatten(); 	
	     	}		    
	        IJ.save(im, dirParam.getAbsolutePath()+"/"+baseName+"_rectangles.tiff");
	    }
	    
	    im.close();		
		
	}
	
	/**
	 * 
	 * @param im
	 */
	private void getDirectionality(ImagePlus im){
		if(verbatim) IJ.log("---- Calculate directionality");

	    ImageProcessor ip = im.getProcessor();
	    ip.autoThreshold();
	    ip.setRoi(new OvalRoi(Xmid - 0.45 * width, Ymid, 0.9 * width, 0.9 * depth));
	    im.setProcessor(ip);	 
	    Directionality dnlty = new Directionality();
		
		// Set fields and settings
		int nbins = 10;
		int binStart = -90;
		
		ImagePlus img = new ImagePlus();
		
		img.setProcessor(ip.duplicate().rotateLeft());
		dnlty.setImagePlus(img);
		
		dnlty.setMethod(Directionality.AnalysisMethod.LOCAL_GRADIENT_ORIENTATION);
		dnlty.setBinNumber(nbins);
		dnlty.setBinStart(binStart);
		dnlty.setBuildOrientationMapFlag(true);
		
		// Do calculation
		dnlty.computeHistograms();
		ResultsTable rs = dnlty.displayResultsTable();
		double angle = 0;
		double tot = 0;
		for(int k = 0; k < rs.getCounter(); k++){
			for(int l = 0; l < (rs.getValueAsDouble(1, k)); l++){
				double direct = rs.getValueAsDouble(0, k);
				double prop = rs.getValueAsDouble(1, k);
				angle += Math.abs(direct)*prop;
				tot += prop;
			}
		}

	    params[counter++] = (float) (angle / tot);
		rs.reset();
		
		im.close();
	}
	
	/**
	 * 
	 * @param im
	 */
	private void getPixelsCount(ImagePlus im, ImagePlus ori){
		if(verbatim) IJ.log("------ Count Tips");
	    ImageProcessor ip = im.getProcessor();
	    ip.autoThreshold();
	    //ip.invert();
	    Roi apex;
	    
	    int nTips = 0;
	    for(int w = 0; w < ip.getWidth(); w++){
	    	for(int h = 0; h < ip.getHeight(); h++){			   
	    		if(ip.get(w, h) > 125){
	    			int n = nNeighbours(ip, w, h);
	    			if(n == 1){
	    				nTips += 1;
	    				if(saveImages && saveTips){
	    				    apex = new OvalRoi(w, h, 10, 10);
	    				    apex.setStrokeColor(Color.blue);
	    				    apex.setStrokeWidth(5);
	    				    Overlay overlay = new Overlay(apex); 
	    				    ori.setOverlay(overlay); 
	    				    ori = ori.flatten(); 	 				
	    				  }
	    			}
	    		}	
	    	}   
	    }
	    if(saveImages && saveTips){
		    // Save the images for post-processing check
		    IJ.save(ori, dirParam.getAbsolutePath()+"/"+baseName+"_tips.jpeg");
	    }
	    im.setProcessor(ip);
	    
	    params[counter++] = nTips;   
	}
	
	/**
	 * 
	 * @param im
	 */
	private void getPixelProfiles(ImagePlus im){
		if(verbatim) IJ.log("------- Pixel profiles");
		float sum, count, max, tot;
		int inc = (int) depth / nSlices;
		
	    im.getProcessor().autoThreshold();
	    
		for(int j = 0; j < nSlices; j++){
			
			sum = 0;
			count = 0;
			max = 0;
			tot = 0;
			
			for(int i = ((j+1) * inc)+(int) bY; i > j * inc; i = i-2){
				Rectangle roi = new Rectangle((int) bX, i, (int) width, 1);
				im.setRoi(roi);
				Analyzer.setResultsTable(rt);
				rt.reset();
				an = new Analyzer(im, Measurements.AREA | Measurements.AREA_FRACTION, rt);
			    an.measure();
			    tot = (float) ((rt.getValue("%Area", 0)/100) * rt.getValue("Area", 0));
				if(tot >= 0){
					sum += (float) tot;
					count++;
				}
				if(tot > max) max = (float) tot;				
			} 
			params[counter++] = sum / count;
			params[counter++] = max;
		}
	
		sum = 0;
		count = 0;
		max = 0;
		
		for(int i = (int) width ; i > 0; i = i-2){
			Rectangle Rect2 = new Rectangle(i, 0, 1, (int) depth);
			im.setRoi(Rect2);
			Analyzer.setResultsTable(rt);
			rt.reset();
			an = new Analyzer(im, Measurements.AREA | Measurements.AREA_FRACTION, rt);
		    an.measure();
		    tot = (float) ((rt.getValue("%Area", 0)/100) * rt.getValue("Area", 0));
			if(tot > 0){
				sum += (float) tot;
				count++;
			}
			if(tot > max) max = (float) tot;
		}
		params[counter++] = sum / count;
		params[counter++] = max;
		
		im.close();
	}
	
	private void getConvexHull(ImagePlus im, boolean efd){
		
		if(verbatim) IJ.log("--------- Get Convex Hull");
		// Get bounding box
        im.getProcessor().autoThreshold();
		im.getProcessor().invert();
		pa = new ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER | ParticleAnalyzer.CLEAR_WORKSHEET, Measurements.AREA, rt, 0, 10e9);
		pa.analyze(im);
		
		// Find the largest object in the image (the root system) in case their are still multiple objects.
		int index = 0;
		double max = 0;
		for(int i = 0; i < rt.getCounter(); i++){
			if(rt.getValue("Area", i) > max){
				max = rt.getValue("Area", i);
				index = i;
			};			
		}
		
		// Get the convex hull from the ROI manager (the largest object)
		RoiManager manager = RoiManager.getInstance();
		Roi[] roiA = manager.getRoisAsArray();
		Roi select = roiA[index];
        		
		ImageProcessor chProcessor = new PolygonRoi(select.getConvexHull(), Roi.POLYGON).getMask(); 			
		ImagePlus chImage = new ImagePlus();
		chImage.setProcessor(chProcessor);
		chProcessor.autoThreshold();
		
		
        if(saveImages){
	        // Make shape
    		Roi roiToOverlay = new PolygonRoi(select.getConvexHull(), Roi.POLYGON); 
    	    roiToOverlay.setStrokeColor(Color.blue);
    	    roiToOverlay.setStrokeWidth(5);
    	    Overlay overlay = new Overlay(roiToOverlay); 
    	    im.getProcessor().invert();
    	    im.setOverlay(overlay); 
    	    im = im.flatten(); 
    	    
		    // Save the images for post-processing check
		    IJ.save(im, dirParam.getAbsolutePath()+"/"+baseName+"_convexhull.jpeg");   
        }
	    // Get shape measurements from the convex hull
		chImage.getProcessor().invert();
		Analyzer.setResultsTable(rt);
		rt.reset();
		pa = new ParticleAnalyzer(ParticleAnalyzer.CLEAR_WORKSHEET, Measurements.CENTER_OF_MASS |
				Measurements.AREA, rt, 0, 10e9);
		pa.analyze(chImage);
		
        if(saveShapes){  
		    // Save the images for post-processing check
		    IJ.save(chImage, shapeFolder+"/"+baseName+"_shape.jpeg");   
        }
		

	    params[counter++] = (float) rt.getValue("Area", 0);  
		if(verbatim) IJ.log("--------- Get Convex Hull4");

	    
		if(efd){
			if(verbatim) IJ.log("EFD analysis");
			PolygonRoi roi = new PolygonRoi(select.getConvexHull(), Roi.POLYGON);
			Rectangle rect = roi.getBounds();
			int n = roi.getNCoordinates();
			double[] x = new double[n];
			double[] y = new double[n];
			int[] xp = roi.getXCoordinates();
			int[] yp = roi.getYCoordinates();  
			for (int i = 0; i < n; i++){
			    x[i] = (double) (rect.x + xp[i]);
			    y[i] = (double) (rect.y + yp[i]); 
			}  
			EllipticFD efd1 = new EllipticFD(x, y, nEFD);
			for (int i = 0; i < efd1.nFD; i++) {
				sendEFDDataToCSV(i+1, efd1.ax[i], efd1.ay[i],efd1.bx[i],efd1.by[i],efd1.efd[i]);
			}	    
		} 	


	}
	
	/**
	 * 
	 * @param im
	 */
	private void getCoordinates(ImagePlus im){
		
		if(verbatim) IJ.log("-------- Get Coordinates");
		// Get bounding box
        im.getProcessor().autoThreshold();
        IJ.run(im, "Create Selection", "");
        
        Roi select;
        select = im.getRoi();
        ImageProcessor Shape = im.getProcessor();
        Shape.setRoi(select.getBounds());
        Shape = Shape.crop();
        im.setProcessor(Shape);
        float w = im.getWidth();
        float h = im.getHeight();
        int m = 2 * nCoord;

        //Calculate coordinates
        // Make rectangle (for each rectangle) 
        // Get bounding box of rectangle
		// Get coordinates of bounding box
		// Save coordinates
        
        for(int i = 0; i < nCoord; i++){
        	ImagePlus currentSelection = im.duplicate();
        	float factor = (float) i / (nCoord - 1);
        
        	float y = Ymid;
        	if(i == 0) y = 0.01f * h;//Ymid;
        	else if(i == (nCoord - 1)) y = (float) (0.99 * h);
        	else y = (float) (factor * h);
        	
        	currentSelection.setRoi(new Roi(0, y, w, 3));
        	
        	ImageProcessor small = currentSelection.getProcessor();
        	small = small.crop();
        	small.setAutoThreshold("Li");
        	currentSelection.setProcessor(small);	
        
	        IJ.run(currentSelection, "Create Selection", "");        
	        Analyzer.setResultsTable(rt);
	        rt.reset();
	        an = new Analyzer(currentSelection, Measurements.RECT, rt);
	        an.measure();
	         
	     	xCoord[i] = (float) rt.getValue("BX", 0);
	     	yCoord[i] = (float) y;
	     
	        int o = m - i - 1;
	        xCoord[o] = (float) (rt.getValue("BX", 0) + rt.getValue("Width", 0));
	     	yCoord[o] = (float) y;  
	     	
	     	// Get the width and the cumul width (inspired from Bucksch et al 2014, Plant Physiology)
	     	diffCoord[i] = Math.abs(xCoord[i] - xCoord[o]) / w;
	     	if(i == 0) cumulCoord[i] = diffCoord[i];
	     	else cumulCoord[i] = cumulCoord[i-1] + diffCoord[i];
        	
        }
        
        if(saveImages){
	        // Make shape
	     	PolygonRoi shapeROI = new PolygonRoi(xCoord,yCoord, Roi.FREEROI);
	     	shapeROI.setStrokeColor(Color.blue);
		    shapeROI.setStrokeWidth(5);
		    Overlay overlay = new Overlay(shapeROI); 
		    im.setOverlay(overlay); 
		    im = im.flatten(); 
	  
		    // Save the images for post-processing check
		    IJ.save(im, dirParam.getAbsolutePath()+"/"+baseName+"_shape.jpeg");   
        }
        
        im.close();

        if(saveTPS) sendShapeDataToTPS(xCoord, yCoord);

        for(int i = 0; i < nCoord*2; i++) params[counter++] = xCoord[i] / w;
        for(int i = 0; i < nCoord; i++) params[counter++] = diffCoord[i];
        for(int i = 0; i < nCoord; i++) params[counter++] = cumulCoord[i];
	}


	/**
	 * Compute the number of black neigbours for a point
	 * @param bp
	 * @param w
	 * @param h
	 * @return
	 */
	private int nNeighbours(ImageProcessor bp, int w, int h){
		int n = 0;
		for(int i = w-1; i <= w+1; i++){
			for(int j = h-1; j <= h+1; j++){
				if(bp.getPixel(i, j) > 125) n++;
				if(n == 3) return n-1;
			}
		}
		return n-1;
	}	

	/**
	 * Print Parameters CSV header
	 */
	private void printAnalysisCSVHeader(){	
		String toPrint = "image";
		toPrint = toPrint.concat(",width,height,time_start, time_end");
		pwAnalysis.println(toPrint);
		pwAnalysis.flush();
	}	
	
	/**
	 * Print Parameters CSV header
	 */
	private void sendAnalysisToCSV(String image, int width, int height, long time1, long time2){
		String toPrint = image+","+width+","+height+","+time1+","+time2;
		pwAnalysis.println(toPrint);
		pwAnalysis.flush();
	}		
	
	/**
	 * Print Parameters CSV header
	 */
	private void printParamCSVHeader(){	
		String toPrint = "image";
		toPrint = toPrint.concat(",diam_max,diam_mean,diam_mode");
		toPrint = toPrint.concat(",length,area,width,depth,width_depth_ratio,com_x,com_y");
		toPrint = toPrint.concat(",ellips_025,ellips_050,ellips_075,ellips_100");
		toPrint = toPrint.concat(",rect_020,rect_040,rect_060,rect_080");
		toPrint = toPrint.concat(",directionality");
		toPrint = toPrint.concat(",tip_count");
		for(int i = 0; i < nSlices; i++) toPrint = toPrint.concat(",cross_hori_"+i+"_mean,cross_hori_"+i+"_max");
		toPrint = toPrint.concat(",cross_vert_mean,cross_vert_max,convexhull");
		for(int i = 0; i < nCoord * 2; i++) toPrint = toPrint.concat(",coord_x"+i);
		for(int i = 0; i < nCoord; i++) toPrint = toPrint.concat(",diff_x"+i);
		for(int i = 0; i < nCoord; i++) toPrint = toPrint.concat(",cumul_x"+i);
		pwParam.println(toPrint);
		pwParam.flush();
	}

	/**
	 * Send parameters data to an CSV file
	 */
	private void sendParametersToCSV(){	
		String toPrint = baseName.replaceAll(",", "-");
		for(int i = 0; i < params.length; i++){
			toPrint = toPrint.concat(","+params[i]);
		}
		pwParam.println(toPrint);
		pwParam.flush();
	}
	
	/**
	 * send Shape Data To CSV
	 */
	private void sendShapeDataToTPS(float[] coordX, float[] coordY){	
		pwTPS.println("ID="+baseName);
		pwTPS.println("LM="+(nCoord*2));
		for(int i = 0; i < coordX.length; i++) pwTPS.println(coordX[i]+" "+coordY[i]);
		pwTPS.flush();
	}
	
	/**
	 * Print EFD CSV header
	 */
	private void printEFDCSVHeader(){	
		pwEFD.println("image, index, ax, ay, bx, by, efd");			
		pwEFD.flush();
	}
	/**
	 * Send EFD data to an CSV file
	 */
	private void sendEFDDataToCSV(int i, double ax, double ay, double bx, double by, double efd){	
		pwEFD.println(baseName +","+ i +","+ ax +","+ ay +","+ bx+","+ by+","+ efd);
		pwEFD.flush();
	}
	
	
	
   /**
    * Image filter	
    * @author guillaumelobet
    */
	public class ImageFilter extends javax.swing.filechooser.FileFilter{
		public boolean accept (File f) {
			if (f.isDirectory()) {
				return true;
			}

			String extension = getExtension(f);
			if (extension != null) {
				if (extension.equals("jpg") || extension.equals("png") ||
						extension.equals("tif") || extension.equals("tiff") || extension.equals("jpeg")) return true;
				else return false;
			}
			return false;
		}
	     
		public String getDescription () {
			return "Image files (*.jpg, *png, *tiff)";
		}
	      
		public String getExtension(File f) {
			String ext = null;
			String s = f.getName();
			int i = s.lastIndexOf('.');
			if (i > 0 &&  i < s.length() - 1) {
				ext = s.substring(i+1).toLowerCase();
			}
			return ext;
		}
	}	
	
}
