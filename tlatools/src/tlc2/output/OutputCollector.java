package tlc2.output;

import java.util.ArrayList;

import tlc2.tool.TLCStateInfo;

public class OutputCollector {

	static ArrayList<TLCStateInfo> trace = null;
	static ArrayList<ErrorMessage> errorMessages = new ArrayList<ErrorMessage>();

	public synchronized static ArrayList<TLCStateInfo> getTrace() {
		return trace;
	}

	public synchronized static void setTrace(ArrayList<TLCStateInfo> trace) {
		OutputCollector.trace = trace;
	}

	public static ArrayList<ErrorMessage> getErrorMessages() {
		return errorMessages;
	}

	public static void addErrorMessage(ErrorMessage errorMessage) {
		OutputCollector.errorMessages.add(errorMessage);
	}
	
	
	
	
	
}
