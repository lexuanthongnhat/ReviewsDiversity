package edu.ucr.cs.dblab.nle020.metamap;

public class SemanticType {
	private String TUI;
	private String type;
	private String fullName;
	
	private String group;

	public SemanticType(String TUI, String type, String fullName, String group) {
		super();
		this.TUI = TUI;
		this.type = type;
		this.fullName = fullName;
		this.group = group;
	}

	public String getTUI() {
		return TUI;
	}

	public String getType() {
		return type;
	}

	public String getFullName() {
		return fullName;
	}

	public String getGroup() {
		return group;
	}

}
