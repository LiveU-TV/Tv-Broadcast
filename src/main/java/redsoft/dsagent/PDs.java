/*
 * (C) Copyright 2016, RedSoft, http://www.redsoft-develop.com
 *
 *                 Debug Stream Agent for Java 
 * 
 * Permission to use, copy, modify, and distribute this software for any
 * purpose, without fee, and without a written agreement, is hereby granted.
 * 
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE TO ANY PARTY FOR DIRECT,
 * INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES,
 * ARISING OUT OF THE USE OF THIS SOFTWARE.
 * 
 * This file is part of Debug Stream Tools project <http://www.redsoft-develop.com>
 * and introduces a sample for simple DS-Agent implementation for Java.
 * 
 * Debug Stream Agent's log data is optimal to read and analyze using 
 * Debug Stream Viewer Windows/Linux application, which may be downloaded from
 * http://www.redsoft-develop.com/DS-viewer.html
 * 
 * Filename	: PDs.java
 * Library  : DS Agent
 * Purpose	: Physical Debug Stream classes
 * Author	: (c) RedSoft, Sergey Krasnitsky
 * Created	: 15/08/2015
 */

package redsoft.dsagent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;


public abstract class PDs 
{
	/*
	 * Static constants
	 */
	static public final int PDS_STD	 = 0; 		// PDS STD type 
	static public final int PDS_ERR	 = 1; 		// PDS STD type 
	static public final int PDS_UDP	 = 2; 		// PDS UDP type 
	static public final int PDS_FILE = 3; 		// PDS FILE type 

	/*
	 * Static members
	 */
	static public void newStd()								{ pdsSet.addLast(new PDsStd()); }
	static public void newErr()								{ pdsSet.addLast(new PDsErr()); }
	static public void newUdp(String host, int port)		{ pdsSet.addLast(new PDsUdp(host, port)); }
	static public void newUdp(String host)					{ pdsSet.addLast(new PDsUdp(host, PDsUdp.DSUDP_DEF_PORT)); }
	static public void newFile(String path, String name)	{ pdsSet.addLast(new PDsFile(path, name)); }
	static public void newFile(String filepath)				{ pdsSet.addLast(new PDsFile(filepath)); }

	static LinkedList<PDs> pdsSet = new LinkedList<PDs>();

	/*
	 * Methods
	 */
	abstract void sendDsMessage(byte[] data);
}


class PDsStd extends PDs 
{
    public void sendDsMessage(byte[] data)
	{
    	System.out.println(DsProt.makeDisplayable(data));
	}
}


class PDsErr extends PDs 
{
    public void sendDsMessage(byte[] data)
	{
    	System.err.println(DsProt.makeDisplayable(data));
	}
}


class PDsUdp extends PDs 
{
	static public final int DSUDP_DEF_PORT = 1410;

	static public InetAddress 		serverAddr;
	static public int 				serverPort;
	static public DatagramSocket 	sock;
	
	PDsUdp (String host, int port)
	{
		try {
			serverAddr = InetAddress.getByName(host);
			sock = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		serverPort = port;
	}

    @Override
    public void finalize() {
		if (sock != null)
			sock.close();
    }
    
    public void sendDsMessage(byte[] data)
	{
		DatagramPacket dp = new DatagramPacket(data, data.length, serverAddr, serverPort);
		try {
			sock.send(dp);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}


class PDsFile extends PDs 
{
	protected String 			pathName;
	protected FileOutputStream 	outFile;
	
	PDsFile (String filepath)
	{
		pathName = filepath;
		String pathBak = pathName + ".bak"; 
		File fbak = new File(pathBak);
		if(fbak.exists())
			fbak.delete();
		File f = new File(pathName);
		if (f.exists())
			f.renameTo(fbak);
	    
		try {
			outFile = new FileOutputStream(pathName);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	PDsFile (String path, String name)
	{
		this(path + File.separator + name);
	}

	@Override
    public void finalize() {
		try {
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public void sendDsMessage(byte[] data)
	{
		try {
			outFile.write(data);
			// outFile.flush();  FileOutputStream has empty flush method because according to the doc, its write op is not buffered
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
