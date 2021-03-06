package wyvern.tools.typedAST.interfaces;

import wyvern.tools.typedAST.core.expressions.Assignment;
import wyvern.tools.types.Environment;

public interface Assignable {
	void checkAssignment(Assignment ass, Environment env);
	Value evaluateAssignment(Assignment ass, Environment env);
}
