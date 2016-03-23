package framework;

import framework.stockage.Stockage;

public class RawMaterial {
	
	private int amount, min, max;
	private boolean variable;
	private Stockage contains;
	private TypeOfRawMaterial kind;
	
	public enum TypeOfRawMaterial {
		unity ("Unit�"),  centiliter ("Centilitre"), gram ("Gramme");
		
		private String name;       
	    private TypeOfRawMaterial(String s) {
	        name = s;
	    }
	    public String toString() {
	    	return name;
	    }
	}
	
	public RawMaterial(int amount, TypeOfRawMaterial kind, Stockage contains) {
		this.amount = amount;
		this.kind = kind;
		this.contains = contains;
	}
	public Stockage getStock() {
		return contains;
	}
	public int getAmount() {
		return amount;
	}

}