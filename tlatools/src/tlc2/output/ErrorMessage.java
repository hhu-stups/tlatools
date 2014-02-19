package tlc2.output;

public class ErrorMessage {
	
	private int errorCode;
	private String[] parameters;
	
	public ErrorMessage(int errorCode, String[] parameters){
		this.errorCode = errorCode;
		this.parameters = parameters;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public String[] getParameters() {
		return parameters;
	}
}
