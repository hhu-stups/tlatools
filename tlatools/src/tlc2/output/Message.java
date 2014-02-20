package tlc2.output;

import java.util.Date;

public class Message {
	
	private int messageClass;
	private int errorCode;
	private String[] parameters;
	private Date date;
	
	public Message(int errorCode, String[] parameters, Date date){
		this.errorCode = errorCode;
		this.parameters = parameters;
		this.date = date;
	}

	public Message(int messageClass, int errorCode, String[] parameters,
			Date date) {
		this.messageClass = messageClass;
		this.errorCode = errorCode;
		this.parameters = parameters;
		this.date = date;
	}

	public int getMessageClass(){
		return messageClass;
	}
	
	public int getMessageCode() {
		return errorCode;
	}

	public String[] getParameters() {
		return parameters;
	}

	public Date getDate(){
		return date;
	}
	
}
