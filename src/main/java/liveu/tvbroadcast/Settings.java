package liveu.tvbroadcast;

import java.io.PrintStream;

import redsoft.dsagent.*;


public class Settings {
	
	public static boolean TEST_MODE = false;	// Is Test-mode enabled
	public static String DS;					// DS target config, it's a string like "STD:UDP:FILE"  
	public static String DS_UDPSRV;				// DS-UDP server (when UDP target is present)
	public static String DS_FILEPATH;			// DS-FILE path to be created (when FILE target is present)
	
	/* Ds */
	private static final Ds ds = new Ds("Settings");

	/**
	 * set setting from system
	 */
	public static void init() {
		TEST_MODE 	= Boolean.valueOf(System.getProperty("test.mode"));
		DS 			= System.getProperty("ds");
		DS_FILEPATH = System.getProperty("ds.file");
		DS_UDPSRV 	= System.getProperty("ds.srv");
	}
	
	public static boolean isPdsActive (int pds_id)
	{
		if (DS==null)
			return false;

		switch (pds_id) {
			case PDs.PDS_STD:	return DS.indexOf("STD") >= 0;
			case PDs.PDS_ERR:	return DS.indexOf("ERR") >= 0;
			case PDs.PDS_UDP:	return DS.indexOf("UDP") >= 0;
			case PDs.PDS_FILE:	return DS.indexOf("FILE") >= 0;
		}
		return false;
	}

	/**
	 * print settings
	 */
	public static void print(PrintStream ps) {
		ps.println("Using settings:");
		ps.println(" test.mode=" + TEST_MODE);
		ps.flush();
	}
	
	/**
	 * print setting 2 Ds
	 */
	public static void print2Ds() {
		ds.print("TEST_MODE=%s", TEST_MODE);
	}
}
