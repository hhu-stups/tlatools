package tlc2.output;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import tla2sany.semantic.ExprNode;
import tla2sany.semantic.ModuleNode;
import tla2sany.st.Location;
import tlc2.tool.TLCState;
import tlc2.tool.TLCStateInfo;

public class OutputCollector {

	private static TLCState initialState = null;
	private static ArrayList<TLCStateInfo> trace = null;
	private static ArrayList<Message> allMessages = new ArrayList<Message>();
	private static Hashtable<Location, Long> lineCount = new Hashtable<Location, Long>();
	private static ModuleNode moduleNode = null;
	private static ExprNode violatedAssumption = null;

	public static ArrayList<TLCStateInfo> getTrace() {
		return trace;
	}

	public static void setTrace(ArrayList<TLCStateInfo> trace) {
		OutputCollector.trace = trace;
	}

	public static void addStateToTrace(TLCStateInfo tlcStateInfo) {
		if (trace == null) {
			trace = new ArrayList<TLCStateInfo>();
		}
		trace.add(tlcStateInfo);
	}

	public static void setInitialState(TLCState initialState) {
		OutputCollector.initialState = initialState;
	}

	public static TLCState getInitialState() {
		return OutputCollector.initialState;
	}

	public static ArrayList<Message> getAllMessages() {
		return allMessages;
	}

	public static void setViolatedAssumption(ExprNode assumption) {
		violatedAssumption = assumption;
	}

	public static ExprNode getViolatedAssumption() {
		return violatedAssumption;
	}

	public synchronized static void saveMessage(int messageClass,
			int messageCode, String[] parameters) {

		Message m = new Message(messageClass, messageCode, parameters,
				new Date());
		allMessages.add(m);
	}

	public static ModuleNode getModuleNode() {
		return moduleNode;
	}

	public static void setModuleNode(ModuleNode moduleNode) {
		OutputCollector.moduleNode = moduleNode;
	}

	public static Hashtable<Location, Long> getLineCountTable() {
		return new Hashtable<Location, Long>(lineCount);
	}

	public static void putLineCount(Location location, long val) {
		lineCount.put(location, val);
	}
}
