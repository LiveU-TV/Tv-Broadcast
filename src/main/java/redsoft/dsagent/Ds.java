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
 * Filename	: Ds.java
 * Library  : DS Agent
 * Purpose	: Debug Stream Agent for Java applications
 * Author	: (c) RedSoft, Sergey Krasnitsky
 * Created	: 15/08/2015
 */

package redsoft.dsagent;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.time.Instant;


class DsProt 
{
	static public final int DSMTYPE_INFO 			= 0; 		// Info: line + opt 4 params
	static public final int DSMTYPE_WARN 			= 1; 		// Warning: line + opt 4 params; file&line present
	static public final int DSMTYPE_ERROR 			= 2; 		// Error: line + opt 4 params; file&line present
	static public final int DSMTYPE_ASSERT 			= 3; 		// Assert: error msg; file&line present
	static public final int DSMTYPE_VERIFY 			= 4; 		// Verify: error msg; file&line present
	static public final int DSMTYPE_FUNCS 			= 5; 		// Function Start: func name present
	static public final int DSMTYPE_FUNCE 			= 6; 		// Function End: func name present
	static public final int DSMTYPE_NUMS 			= 7; 		// Number of basic message types

	static public final int DSMTYPE_BASIC_MASK		= 7;		// Mask of the type basic bits (Type=7 is a spare for now)

	// Byte #0, bits 3..7 - Various flags
	static public final int DSMTYPE_FORMATTED		= 0x08;		// TRMSG::str is raw string or already formatted
	static public final int DSMTYPE_VERINFO			= 0x10;		// TRMSG::str contains version information. TRMSG::str has special format
	static public final int DSMTYPE_DUP				= 0x20;		// The message is from Duplicate Buffer 

	// Byte #1; Session/Thread/Instances Control flags
	static public final int DSMTYPE_SESSION_START	= 0x0100;	// New session (process) started. TRMSG::str has special format
	static public final int DSMTYPE_SESSION_END		= 0x0200;	// Session (process) ended
	static public final int DSMTYPE_THREAD_START	= 0x0400;	// New thread started. TRMSG::str has special format
	static public final int DSMTYPE_THREAD_END		= 0x0800;	// Thread ended
	static public final int DSMTYPE_THREAD_SWITCH	= 0x1000;	// Thread context switch
	static public final int DSMTYPE_INSTANCE_NEW	= 0x2000;	// New DS Instance created
	static public final int DSMTYPE_INSTANCE_DEL	= 0x4000;	// DS Instance deleted 

	// Shortcuts
	static public final int DSMTYPE_MAIN_START		= DSMTYPE_SESSION_START | DSMTYPE_THREAD_START;
	static public final int DSMTYPE_MAIN_END		= DSMTYPE_SESSION_END | DSMTYPE_THREAD_END;
	
	// Length
	static public final int DSMSG_LEN_HEADER 		=  56;		// sizeof(Dsprot::Hdr)
	static public final int DSMSG_LEN_STRFIELDS 	= 512;		// sizeof(Dsprot::StrFields)

	// Misc
	static public final int DSMSG_MAGIC 			= 0x0100CAFE; 
	
	static private final AtomicInteger nseq = new AtomicInteger(0); 

	static public String time2String(long time) {
	    String format = "yyyy-MM-dd HH:mm:ss.SSS";
	    SimpleDateFormat sdf = new SimpleDateFormat(format);
	    //sdf.setTimeZone(TimeZone.getDefault());
	    return sdf.format(new Date(time / 1000));
	}
	
	static public long getCurTime() {
	    //return System.nanoTime() / 1000;
	    return Instant.now().toEpochMilli() * 1000;
	}

	static public byte[] prepBuffer (String dsname, int type, long time, long tid, int level, long dskey, int line, String file, String func, String text) 
			throws UnsupportedEncodingException, Exception 
	{
		int len_next = dsname.length() + file.length() + func.length() + 4 /* 4 bytes is for 4 termination '\0' added below */;
		
		if (len_next >= DsProt.DSMSG_LEN_STRFIELDS)
			throw new Exception("Too long DS fields: dsname, file or func");
		
		int ltext = text.length();
		len_next += ltext;

		if (len_next >= DsProt.DSMSG_LEN_STRFIELDS) {
			int ltext2 = ltext - (len_next - DsProt.DSMSG_LEN_STRFIELDS);
			text = text.substring(0, ltext2);
			len_next -= ltext - ltext2;
		}

		ByteBuffer buf = ByteBuffer.allocate(DsProt.DSMSG_LEN_HEADER + len_next);

		buf.putInt	(type);
		buf.putInt	(DSMSG_MAGIC);
		buf.putLong	(time);
		buf.putLong	(tid);
		buf.putInt	(nseq.incrementAndGet());
		buf.putInt	(level);
		buf.putLong	(dskey);
		buf.putInt	(line);

		byte off_dsname = 0;

		buf.put(off_dsname);	byte off_file = (byte) (dsname.length() + 1);
		buf.put(off_file);		byte off_func = (byte) (off_file + file.length() + 1);
		buf.put(off_func);		byte off_text = (byte) (off_func + func.length() + 1);
		buf.put(off_text);

		buf.putInt	(len_next);
		buf.putInt	(0/*reserved2*/);

		buf.put(dsname.getBytes("US-ASCII"));	buf.put((byte) 0);
		buf.put(file.getBytes("US-ASCII"));		buf.put((byte) 0);
		buf.put(func.getBytes("US-ASCII"));		buf.put((byte) 0);
		buf.put(text.getBytes("US-ASCII"));		buf.put((byte) 0);

		buf.flip();

		return buf.array();
	}

	static public String makeDisplayable (byte[] dsmsg) 
	{
		ByteBuffer buf = ByteBuffer.wrap(dsmsg);

		int type 	= buf.getInt();
		buf.getInt(); // magic
		long time 	= buf.getLong();
		/*long tid=*/ buf.getLong();
		int  nseq 	= buf.getInt();
		/*int level=*/buf.getInt();
		long dskey 	= buf.getLong();
		int  line 	= buf.getInt();

		byte off_dsname = buf.get();	
		byte off_file 	= buf.get();
		byte off_func 	= buf.get();
		byte off_text 	= buf.get();
		int  len_next   = buf.getInt();
		buf.getInt(); // reserved2
		
		int x;
		x = off_file - off_dsname; 	byte[] dsname = new byte[x];	buf.get (dsname, 0, x-1);		buf.get(); /*get last '\0'*/ 
		x = off_func - off_file;	byte[] file	  = new byte[x];	buf.get (file, 	 0, x-1);		buf.get(); 
		x = off_text - off_func;	byte[] func	  = new byte[x];	buf.get (func, 	 0, x-1);		buf.get(); 
		x = len_next - off_text;	byte[] text	  = new byte[x];	buf.get (text, 	 0, x-1);		buf.get(); 

	    String outstr = "DS>> " + time2String(time) + " #" + nseq + " >> " + new String(dsname);
		int dsname_key_len = dsname.length; 
		if (dskey != 0) {
			int l = outstr.length();
			outstr += '(' + Long.toHexString(dskey) + ')';
			dsname_key_len += outstr.length() - l;
		}
		
		final int DSNAME_WITH_KEY_LEN = 18;
		int spaces = DSNAME_WITH_KEY_LEN - dsname_key_len;
		if (spaces > 0) {
			final char[] spaceArray = new char[spaces];
			Arrays.fill(spaceArray, ' ');
			outstr += new String(spaceArray);
		}

	    outstr += " >> ";

	    final String[] trdataPrefix  = 
    	{ 
    	    "INFO: ",
    	    "WARNING ", 
    	    "ERROR ",
    	    "ASSERT failure ", 
    	    "VERIFY failure ",
    	    "-> ",
    	    "<- "
    	};

	    int basictype = type & DSMTYPE_BASIC_MASK;
	    switch (basictype)
	    {
	        case DSMTYPE_FUNCS:
	        case DSMTYPE_FUNCE:
	            outstr += trdataPrefix[basictype] + new String(func);
	            return outstr;

	        case DSMTYPE_ERROR:
	        case DSMTYPE_ASSERT:
	        case DSMTYPE_VERIFY:
	        case DSMTYPE_WARN:
	            outstr += trdataPrefix[basictype] + '[' + new String(file) + ':' + line + "] ";
	            break;

	        case DSMTYPE_INFO:
	            break;
	    }

	    outstr += new String(text);
	    return outstr;
	}
}


public class Ds
{
	static public void Init ()
	{
		//long now = Instant.now().toEpochMilli() * 1000;
		dsSys = new Ds("SYS");
	}

	static public Ds dsSys;	// Common system DS

	private static void sendDsMessage(byte[] data)
	{
    	for (PDs pds: PDs.pdsSet)
    	    pds.sendDsMessage(data);
    }


	/*
	 * Members
	 */

	public String name;
	

	/*
	 * Public Methods
	 */
	
	public Ds (String name)
	{
		this.name = name;
	}

	public void print (String sformat, Object... arguments)
	{
		print(0, sformat, arguments);
	}

	public void print (int level, String sformat, Object... arguments)
	{
		try {
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_INFO,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							level,
							hashCode(),		// actually it is not an unique ID
							0,
							"",
							"",
							String.format(sformat, arguments)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void error (String sformat, Object... arguments)
	{
		StackTraceElement[] exestack = Thread.currentThread().getStackTrace();
		
		try {
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_ERROR,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							0,
							hashCode(),		// actually it is not an unique ID
							exestack[1].getLineNumber(),
							exestack[1].getClassName(),
							exestack[1].getMethodName(),
							String.format(sformat, arguments)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void warning (String sformat, Object... arguments)
	{
		StackTraceElement[] exestack = Thread.currentThread().getStackTrace();
		
		try {
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_WARN,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							0,
							hashCode(),		// actually it is not an unique ID
							exestack[1].getLineNumber(),
							exestack[1].getClassName(),
							exestack[1].getMethodName(),
							String.format(sformat, arguments)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void funcs(String func)
	{
		funcs(0, func);
	}

	public void funcs(int level, String func) {
		try {
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_FUNCS,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							level,
							hashCode(),		// actually it is not an unique ID
							0,
							"",
							func,
							""
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void funce(String func)
	{
		funce(0, func);
	}

	public void funce(int level, String func) {
		try {
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_FUNCE,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							level,
							hashCode(),		// actually it is not an unique ID
							0,
							"",
							func,
							""
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void threadInfo(String threadname, int flags)
	{
		try {
			long tid = Thread.currentThread().getId();
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							flags,
							DsProt.getCurTime(),
							tid,
							0,
							0,
							0,
							"",
							"",
							String.format("THREAD STARTED:  %s, TID=%X", threadname, tid)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void versionInfo(String appname, String verstring)
	{
		try {
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_VERINFO | DsProt.DSMTYPE_SESSION_START,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							0,
							0,
							0,
							"",
							"",
							String.format("%s version: %s", appname, verstring)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void threadInfo(String threadname)
	{
		threadInfo(threadname, DsProt.DSMTYPE_THREAD_START);
	}

	public void MainInfo(String processname)
	{
		threadInfo(processname, DsProt.DSMTYPE_MAIN_START);
	}

	public void mainInfo(String processname, String appname, String verstring)
	{
		threadInfo(processname, DsProt.DSMTYPE_MAIN_START);
		versionInfo(appname, verstring);
	}

	public void instanceNew(int level, String instancename)
	{
		try {
			int key = hashCode();
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_INSTANCE_NEW,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							level,
							key,
							0,
							"",
							"",
							String.format("NEW INSTANCE: %s, KEY=%X, NAME=%s", name, key, instancename)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void instanceNew(String instancename)
	{
		instanceNew(0,instancename);
	}

	public void instanceDel(int level)
	{
		try {
			int key = hashCode();
			sendDsMessage(
					DsProt.prepBuffer(
							name,
							DsProt.DSMTYPE_INSTANCE_DEL,
							DsProt.getCurTime(),
							Thread.currentThread().getId(),
							level,
							key,
							0,
							"",
							"",
							String.format("DEL INSTANCE: KEY=%X", name, key)
						)
					);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void instanceDel()
	{
		instanceDel(0);
	}

}
