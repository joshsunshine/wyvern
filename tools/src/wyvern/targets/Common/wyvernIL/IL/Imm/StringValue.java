package wyvern.targets.Common.wyvernIL.IL.Imm;

import wyvern.targets.Common.wyvernIL.IL.visitor.OperandVisitor;

public class StringValue implements Operand {

	private String value;

	public StringValue(String value) {
		this.value = value;
	}

	@Override
	public <R> R accept(OperandVisitor<R> visitor) {
		return visitor.visit(this);
	}

	public String isValue() {
		return value;
	}
	@Override
	public String toString() {
		return "\""+value+"\"";
	}
}
