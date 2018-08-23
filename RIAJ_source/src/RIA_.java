/**
* @author Guillaume Lobet | Universite de Liege
* @date: 2013-02-15
* 
* Plugin containing usefull function for the analysis of Arabidopsis rosette size
* 
**/

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.prefs.Preferences;

import ij.ImageJ;
import ij.plugin.frame.PlugInFrame;


public class RIA_ extends PlugInFrame{
	
	static RIA_ instance, fop;
	public static Preferences prefs;


//	private static final long serialVersionUID = 42L;

	public RIA_() {
		super("Root Image Analysis J");
		
		new RIAInterface();
	}
	
	
	public static void main(String args[]) {
		if(args.length > 0){
			if(args.length == 6){
				new RootAnalysis(new File(args[0]), args[1], Float.valueOf(args[2]), 2.54f, 
					true, 50, false, true, true, Boolean.valueOf(args[3]), true, Boolean.valueOf(args[4]), args[5] 
					);
			}
			if(args.length > 6){
				new RootAnalysis(new File(args[0]), args[1], Float.valueOf(args[2]), 2.54f, 
					true, 50, false, Boolean.valueOf(args[6]), Boolean.valueOf(args[7]), 
					Boolean.valueOf(args[3]), false, Boolean.valueOf(args[4]), args[5] 
					);
			}			
			System.exit(0);
		}
		else{
			prefs = Preferences.userRoot().node("/ImageJ/plugins");
			ImageJ ij = new ImageJ();
			fop = new RIA_();
			ij.addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {
					fop.dispose();
					System.exit(0);
				}
			});
		}
		//System.exit(0);
	}
}
