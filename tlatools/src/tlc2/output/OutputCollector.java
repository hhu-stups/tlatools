package tlc2.output;

import java.util.ArrayList;
import java.util.Date;

import tlc2.tool.TLCStateInfo;

public class OutputCollector {

	private static ArrayList<TLCStateInfo> trace = null;
	private static ArrayList<Message> allMessages = new ArrayList<Message>();

	public static ArrayList<TLCStateInfo> getTrace() {
		return trace;
	}

	public static void setTrace(ArrayList<TLCStateInfo> trace) {
		OutputCollector.trace = trace;
	}

	public static ArrayList<Message> getAllMessages() {
		return allMessages;
	}

	public synchronized static void saveMessage(int messageClass,
			int messageCode, String[] parameters) {

		Message m = new Message(messageClass, messageCode, parameters,
				new Date());
		allMessages.add(m);
	}
}
