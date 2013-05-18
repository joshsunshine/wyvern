package wyvern.targets.Java.visitors;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import wyvern.tools.typedAST.abs.Declaration;
import wyvern.tools.typedAST.core.Sequence;
import wyvern.tools.typedAST.core.declarations.ClassDeclaration;
import wyvern.tools.typedAST.core.declarations.DeclSequence;
import wyvern.tools.typedAST.core.declarations.MethDeclaration;
import wyvern.tools.typedAST.core.declarations.ValDeclaration;
import wyvern.tools.typedAST.core.declarations.VarDeclaration;
import wyvern.tools.typedAST.visitors.BaseASTVisitor;
import wyvern.tools.types.Type;
import wyvern.tools.types.extensions.Arrow;
import wyvern.tools.types.extensions.Tuple;
import wyvern.tools.types.extensions.Unit;
import wyvern.tools.util.Pair;

import static org.objectweb.asm.Opcodes.*;

public class ClassVisitor extends BaseASTVisitor {
	private String typePrefix;
	private ClassStore store = null;
	private ExternalContext context = new ExternalContext();
	private List<MethDeclaration> meths = new ArrayList<MethDeclaration>();

	public ClassVisitor(String typePrefix, ClassStore store) {
		this.typePrefix = typePrefix;
		this.store = store;
		context = new ExternalContext();
	}
	
	
	public ClassVisitor(String typePrefix, ClassVisitor pcv, List<Pair<String, Type>> list) {
		this.typePrefix = typePrefix;
		this.store = pcv.store;
		context.setVariables(list, ExternalContext.EXTERNAL);
	}

    public Iterable<Pair<String, Type> > getExternalVars() {
        return context.getExternalDecls();
    }

	private ClassWriter cw = null;

	private String getTypeName(Type type) {
		return getTypeName(type, true);
	}
	
	private String getTypeName(Type type, boolean isUnitVoid) {
		return store.getTypeName(type, isUnitVoid);
	}
	
	private void registerClass(Type type) {
		store.registerClass(type);
	}
	
	private void registerClass(Type type, byte[] bytecode) {
		store.registerClass(type, bytecode);
	}

	private void pushClassType(MethodVisitor mv, String descriptor) {
		if (descriptor.equals("V")) {
			mv.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
		} else if (descriptor.equals("I")) {
			mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
		} else
			mv.visitLdcInsn(org.objectweb.asm.Type.getType(descriptor));
	}

	private Type currentType;
	private String currentTypeName;

	@Override
	public void visit(ClassDeclaration classDecl) {
		// No support for inner classes (yet)
		for (Declaration decl : classDecl.getDecls().getDeclIterator()) {
			context.setVariable(decl.getName(), decl.getType(), ExternalContext.INTERNAL);
		}

        registerClass(classDecl.getType());
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); // Makes it a LOT slower. Easier, though. TODO: Actually implement own stack/var size determination.
		cw.visit(V1_7, 
				ACC_PUBLIC,
                store.getRawTypeName(classDecl.getType()),
				null, 
				"java/lang/Object", 
				null);
		
		currentType = classDecl.getType();
		currentTypeName = store.getRawTypeName(classDecl.getType());

		addDefaultConstructor();
		
		super.visit(classDecl);
		
		for (Pair<String, Type> externalVar : context.getExternalDecls()) {

			cw.visitField(ACC_PUBLIC | ACC_STATIC, externalVar.first + "$stat",
					getTypeName(externalVar.second),
					null, 
					new Integer(0)).visitEnd();

            cw.visitField(ACC_PRIVATE, externalVar.first + "$dyn",
                    getTypeName(externalVar.second),
                    null,
                    new Integer(0)).visitEnd();
		}
		initializeMethodHandles();
		cw.visitEnd();
		registerClass(classDecl.getType(), cw.toByteArray());
	}

	private void initializeMethodHandles() {
		MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
		Label start = new Label();
		Label exceptionBlock = new Label();
		Label exceptionEnd = new Label();
		Label handler = new Label();
		mv.visitTryCatchBlock(exceptionBlock, exceptionEnd, handler, "java/lang/Exception");
		Label returnStatement = new Label();

		mv.visitLabel(start);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
		mv.visitIntInsn(ASTORE, 0);
		pushClassType(mv, store.getTypeName(currentType, true));
		mv.visitIntInsn(ASTORE, 1);

		mv.visitLabel(exceptionBlock);

		for (MethDeclaration md : meths) {
			mv.visitIntInsn(ALOAD,0);
			mv.visitIntInsn(ALOAD,1);
			mv.visitLdcInsn(md.getName());
			StringBuilder sb = new StringBuilder("(Ljava/lang/Class;");
			Arrow arrow = (Arrow)md.getType();
			pushClassType(mv, store.getTypeName(arrow.getResult(), true));
			if (arrow.getArgument() instanceof Tuple) {
				Type[] types = ((Tuple)arrow.getArgument()).getTypes();
				if (types.length < 2) {
					for (Type type : types){
						sb.append("Ljava/lang/Class;");
						pushClassType(mv, store.getTypeName(type, true));
					}
				} else {
					int nth = 0;
					sb.append("[Ljava/lang/Class;");
					mv.visitLdcInsn(types.length);
					mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
					mv.visitInsn(DUP);
					mv.visitIntInsn(ASTORE,2);
					for (Type type : types) {
						mv.visitInsn(DUP);
						mv.visitLdcInsn(nth);
						pushClassType(mv, store.getTypeName(types[0], true));
						mv.visitInsn(AASTORE);
						++nth;
					}
				}
			} else if (!(arrow.getArgument() instanceof Unit)) {
				sb.append("Ljava/lang/Class;");
				pushClassType(mv, store.getTypeName(arrow.getArgument(), true));
			}
			sb.append(")Ljava/lang/invoke/MethodType;");
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType","methodType",sb.toString());
			if (!md.isClassMeth())
				mv.visitMethodInsn(INVOKEVIRTUAL,
						"java/lang/invoke/MethodHandles$Lookup",
						"findVirtual",
						"(Ljava/lang/Class;" +
								"Ljava/lang/String;" +
								"Ljava/lang/invoke/MethodType;)" +
								"Ljava/lang/invoke/MethodHandle;");
			else
				mv.visitMethodInsn(INVOKEVIRTUAL,
						"java/lang/invoke/MethodHandles$Lookup",
						"findStatic",
						"(Ljava/lang/Class;" +
								"Ljava/lang/String;" +
								"Ljava/lang/invoke/MethodType;)" +
								"Ljava/lang/invoke/MethodHandle;");

			mv.visitFieldInsn(
					PUTSTATIC,
					store.getRawTypeName(getCurrentType()),
					md.getName()+"$handle","Ljava/lang/invoke/MethodHandle;");
		}
		mv.visitLabel(exceptionEnd);
		mv.visitJumpInsn(GOTO, returnStatement);
		mv.visitLabel(handler);
		mv.visitIntInsn(ASTORE, 2);
		mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V");
		mv.visitInsn(ATHROW);
		mv.visitLabel(returnStatement);
		mv.visitInsn(RETURN);
		mv.visitMaxs(0,0);
		mv.visitEnd();
	}

	private void addDefaultConstructor() {
		MethodVisitor defaultCstr = cw.visitMethod(ACC_PRIVATE, 
				"<init>", 
				"()V", 
				null,
				null);
		defaultCstr.visitCode();
		defaultCstr.visitIntInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
		defaultCstr.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		defaultCstr.visitInsn(org.objectweb.asm.Opcodes.RETURN);
		defaultCstr.visitMaxs(0, 0);//Unused
		defaultCstr.visitEnd();
	}
	
	@Override
	public void visit(Sequence sequence) {
		if (sequence instanceof DeclSequence)
			for (Declaration decl : ((DeclSequence)sequence).getDeclIterator()) {
				if (decl instanceof ClassDeclaration)
					store.registerClass(decl.getType());
			}
		
		super.visit(sequence);
	}

	@Override
	public void visit(ValDeclaration valDeclaration) {
		super.visit(valDeclaration);
		cw.visitField(ACC_PRIVATE,
				valDeclaration.getName(), 
				getTypeName(valDeclaration.getBinding().getType()),
				null, 
				new Integer(0)).visitEnd();
	}

	@Override
	public void visit(VarDeclaration valDeclaration) {
		super.visit(valDeclaration);
		cw.visitField(ACC_PUBLIC,
				valDeclaration.getName(), 
				getTypeName(valDeclaration.getBinding().getType()),
				null, 
				new Integer(0)).visitEnd();
	}
	
	@Override
	public void visit(MethDeclaration methDeclaration) {
		int access = ACC_PUBLIC;
		if (methDeclaration.isClassMeth())
			access += ACC_STATIC;


		cw.visitField(ACC_PUBLIC | ACC_STATIC,
				methDeclaration.getName() + "$handle",
				org.objectweb.asm.Type.getType(MethodHandle.class).getDescriptor(),
				null,
				null).visitEnd();

		meths.add(methDeclaration);

		MethodVisitor mv = cw.visitMethod(access, methDeclaration.getName(),
				getTypeName(methDeclaration.getType(), false), null, null);
		new MethVisitor(this, mv, context.getExternalDecls()).visit(methDeclaration);
	}

	public String getCurrentTypeName() {
		return currentTypeName;
	}

	public Type getCurrentType() {
		return currentType;
	}



	public ClassStore getStore() {
		return store;
	}
}
