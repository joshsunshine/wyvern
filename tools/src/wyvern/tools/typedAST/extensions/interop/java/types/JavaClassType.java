package wyvern.tools.typedAST.extensions.interop.java.types;

import wyvern.tools.typedAST.core.expressions.Invocation;
import wyvern.tools.typedAST.core.declarations.ClassDeclaration;
import wyvern.tools.typedAST.extensions.interop.java.Util;
import wyvern.tools.typedAST.extensions.interop.java.typedAST.JavaClassDecl;
import wyvern.tools.typedAST.interfaces.Value;
import wyvern.tools.types.Environment;
import wyvern.tools.types.MetaType;
import wyvern.tools.types.Type;
import wyvern.tools.types.extensions.ClassType;
import wyvern.tools.util.Reference;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedList;

public class JavaClassType extends ClassType implements MetaType {
	private final Class clazz;
	private final JavaClassDecl decl;

	@Override
	public String getName() {
		return clazz.getSimpleName();
	}


	public JavaClassType(JavaClassDecl cd) {
		super(new Reference<Environment>() {
			@Override
			public Environment get() {
				cd.initalize();
				return cd.getObjEnv();
			}
		}, null, new LinkedList<>(), "");
		this.clazz = cd.getClazz();
		decl = cd;
	}

	public void initalize() {
		(decl).initalize();
	}

	public Class getInnerClass() {
		decl.initalize();
		return decl.getClazz();
	}

	@Override
	public Type checkOperator(Invocation opExp, Environment env) {
		initalize();
		return super.checkOperator(opExp,env);
	}

	@Override
	public ClassDeclaration getDecl() {
		return decl;
	}

	@Override
	public boolean subtype(Type other) {
		decl.initalize();
		if (other instanceof JavaClassType
				&& ((JavaClassType)other).decl.getClazz().equals(decl.getClazz()))
			return true;
		return super.subtype(other);
	}


	@Override
	public Value getMetaObj() {

		Method creator = Arrays.asList(decl.getClazz().getDeclaredMethods()).stream()
												.filter(meth-> Modifier.isStatic(meth.getModifiers()))
												.filter(meth->meth.getName().equals("meta$get"))
												.filter(meth->meth.getParameterCount() == 0)
												.findFirst()
												.orElseThrow(() -> new RuntimeException("Cannot find meta obj creator method"));
		try {
			Object result = creator.invoke(null);
			return Util.toWyvObj(result);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	@Override
	public String toString() {
		return "JavaClass("+decl.getClazz().getName()+")";
	}
}
