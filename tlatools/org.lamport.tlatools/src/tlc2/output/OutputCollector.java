package tlc2.output;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tla2sany.semantic.ExprNode;
import tla2sany.semantic.ModuleNode;
import tla2sany.st.Location;

import tlc2.tool.TLCState;
import tlc2.tool.TLCStateInfo;

public class OutputCollector {

	private static TLCState initialState = null;
	private static List<TLCStateInfo> trace = null;
	private static List<Message> allMessages = new ArrayList<>();
	private static Map<Location, Long> lineCount = new HashMap<>();
	private static ModuleNode moduleNode = null;
	private static List<ExprNode> violatedAssumptions = new ArrayList<>();

	public static List<TLCStateInfo> getTrace() {
		return Collections.unmodifiableList(trace);
	}

	public static void setTrace(List<TLCStateInfo> trace) {
		OutputCollector.trace = trace;
	}

	public static void addStateToTrace(TLCStateInfo tlcStateInfo) {
		if (trace == null) {
			trace = new ArrayList<>();
		}
		trace.add(tlcStateInfo);
	}

	public static void setInitialState(TLCState initialState) {
		OutputCollector.initialState = initialState;
	}

	public static TLCState getInitialState() {
		return OutputCollector.initialState;
	}

	public static List<Message> getAllMessages() {
		return Collections.unmodifiableList(allMessages);
	}

	public static void addViolatedAssumption(ExprNode assumption) {
			violatedAssumptions.add(assumption);
	}

	public static List<ExprNode> getViolatedAssumptions() {
		return Collections.unmodifiableList(violatedAssumptions);
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

	public static Map<Location, Long> getLineCountTable() {
		return Collections.unmodifiableMap(lineCount);
	}

	public static void putLineCount(Location location, long val) {
		lineCount.put(location, val);
	}
}
