package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import model.ModelInJar;
import tlc2.output.MP;

public class MailSender {

	public static final String MODEL_NAME = "modelName";
	public static final String SPEC_NAME = "specName";
	public static final String MAIL_ADDRESS = "result.mail.address";

	private static void throttleRetry(final String msg, long minutes) {
		try {
			System.err.println(msg);
			System.out.println(msg);
			Thread.sleep(minutes * 60L * 1000L);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	private static List<MXRecord> getMXForDomain(String aDomain) throws NamingException {
		final InitialDirContext ctx = new InitialDirContext();
		final Attributes attributes = ctx.getAttributes("dns:/" + aDomain,
				new String[] { "MX" });
		final Attribute attr = attributes.get("MX");

		final List<MXRecord> list = new ArrayList<MXRecord>();
		
		// RFC 974
		if (attr == null) {
			list.add(new MXRecord(0, aDomain));
		} else {
			// split pref from hostname
			for (int i = 0; i < attr.size(); i++) {
				Object object = attr.get(i);
				if (object != null && object instanceof String) {
					String[] split = ((String) object).split("\\s+");
					if (split != null && split.length == 2) {
						Integer weight = Integer.parseInt(split[0]);
						list.add(new MXRecord(weight, split[1]));
					}
				}
			}
		}
		
		// sort (according to weight of mxrecord)
		Collections.sort(list);
		
		return list;
	}
	
	private static class MXRecord implements Comparable<MXRecord> {
		public Integer weight;
		public String hostname;
		
		public MXRecord(int aWeight, String aHostname) {
			weight = aWeight;
			hostname = aHostname;
		}

		public int compareTo(MXRecord o) {
			return weight.compareTo(o.weight);
		}
	}
	
	// For testing only.
	public static void main(String[] args) throws FileNotFoundException, UnknownHostException {
		MailSender mailSender = new MailSender();
		mailSender.send();
	}

	
	private String modelName = "unknown model";
	private String specName = "unknown spec";
	private File err;
	private File out;

	public MailSender() throws FileNotFoundException, UnknownHostException {
		ModelInJar.loadProperties(); // Reads result.mail.address and so on.
		final String mailto = System.getProperty(MAIL_ADDRESS);
		if (mailto != null) {
			// Record/Log output to later send it by email
			final String tmpdir = System.getProperty("java.io.tmpdir");
			this.out = new File(tmpdir + File.separator + TLAConstants.Files.MODEL_CHECK_OUTPUT_FILE);
			ToolIO.out = new LogPrintStream(out);
			this.err = new File(tmpdir + File.separator + TLAConstants.Files.MODEL_CHECK_ERROR_FILE);
			ToolIO.err = new ErrLogPrintStream(err);
		}
	}
	
	public MailSender(String mainFile) throws FileNotFoundException, UnknownHostException {
		this();
		setModelName(mainFile);
	}
	
	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public void setSpecName(String specName) {
		this.specName = specName;
	}

	public boolean send() {
		return send(new ArrayList<File>());
	}

	public boolean send(List<File> files) {
		// ignore, just signal everything is fine
		return true;
	}	
    
	/**
	 * @return The human readable lines in the log file. 
	 */
	private String extractBody(File out) {
		StringBuffer result = new StringBuffer();
		try {
			final Scanner scanner = new Scanner(out);
			while (scanner.hasNext()) {
				final String line = scanner.nextLine();
				if (line != null && !line.startsWith(MP.DELIM)) {
					result.append(line);
					result.append("\n");
				}
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			result.append("Failed to find file " + out.getAbsolutePath()); 
		}
		return result.toString();
	}

	/**
	 * A LogPrintStream writes the logging statements to a file _and_ to
	 * System.out.
	 */
    private static class LogPrintStream extends PrintStream {

    	public LogPrintStream(File file) throws FileNotFoundException  {
    		super(new FileOutputStream(file));
		}

    	/* (non-Javadoc)
    	 * @see java.io.PrintStream#println(java.lang.String)
    	 */
    	public void println(String str) {
    		System.out.println(str);
    		super.println(str);
    	}
    }
    
    private static class ErrLogPrintStream extends PrintStream {
    	public ErrLogPrintStream(File file) throws FileNotFoundException  {
    		super(new FileOutputStream(file));
		}

    	/* (non-Javadoc)
    	 * @see java.io.PrintStream#println(java.lang.String)
    	 */
    	public void println(String str) {
    		System.err.println(str);
    		super.println(str);
    	}
    }
}
