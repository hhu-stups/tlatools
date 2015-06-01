package tlc2.output;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import tla2sany.semantic.SemanticNode;
import tla2sany.st.Location;
import tlc2.module.TLC;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.TLCStateInfo;
import tlc2.util.ObjLongTable;

public class OutputCollector {

	
	private static TLCState initialState = null;
	private static ArrayList<TLCStateInfo> trace = null;
	private static ArrayList<Message> allMessages = new ArrayList<Message>();
	public static Action nextPred = null;
	public static Hashtable<Location, Long> lineCount= new Hashtable<Location, Long>();
	
	public static ArrayList<TLCStateInfo> getTrace() {
		return trace;
	}

	public static void setTrace(ArrayList<TLCStateInfo> trace) {
		OutputCollector.trace = trace;
	}
	
	public static void addStateToTrace(TLCStateInfo tlcStateInfo){
		if(trace == null){
			trace = new ArrayList<TLCStateInfo>();
		}
		trace.add(tlcStateInfo);
	}

	public static void setInitialState(TLCState initialState){
		OutputCollector.initialState = initialState;
	}
	
	public static TLCState getInitialState(){
		return OutputCollector.initialState;
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
