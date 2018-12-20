package cn.moyada.function.validator.translator;

import cn.moyada.function.validator.annotation.Check;
import cn.moyada.function.validator.annotation.Validation;
import cn.moyada.function.validator.util.CTreeUtil;
import cn.moyada.function.validator.util.RuleHelper;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * 校验逻辑生成器
 * @author xueyikang
 * @since 1.0
 **/
public class CheckTranslator extends BaseTranslator {

    // 默认前置信息
    private final static String DEFAULT_MESSAGE = "invalid argument";

    // 校验规则对象
    private Collection<String> ruleClass;

    public CheckTranslator(Context context, Collection<? extends Element> ruleClass) {
        super(context);
        this.ruleClass = new ArrayList<>(ruleClass.size());
        for (Element rule : ruleClass) {
            this.ruleClass.add(rule.asType().toString());
        }
    }

    /**
     * 创建该方法的报错信息
     * @param methodDecl
     * @return
     */
    private String getPrefixInfo(JCTree.JCMethodDecl methodDecl) {
        String className = CTreeUtil.getOriginalTypeName(methodDecl.sym.getEnclosingElement());
        String methodName = methodDecl.name.toString();
        return " while attempting to access " + className + "." + methodName + "(), cuz ";
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl methodDecl) {
        super.visitMethodDef(methodDecl);

        // 无需校验的方法
        if (isJump(methodDecl)) {
            return;
        }

        // 获取前置信息
        String prefix = getPrefixInfo(methodDecl);

        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();

        // 创建临时变量提取引用
        JCTree.JCVariableDecl msg = newVar("_MSG", 0L, String.class.getName(), null);
        JCTree.JCIdent ident = treeMaker.Ident(msg.name);

        CheckInfo checkInfo;
        for (JCTree.JCVariableDecl param : methodDecl.params) {
            Check annotation = param.sym.getAnnotation(Check.class);
            if (null == annotation) {
                continue;
            }

            // 默认异常类
            String exception = getException(param.sym.getAnnotationMirrors());
            if (null == exception) {
                exception = IllegalArgumentException.class.getName();
            }

            checkInfo = new CheckInfo(exception, annotation.message(), annotation.nullable());
            buildLogic(statements, ident, param, prefix, checkInfo);
        }

        statements.addAll(methodDecl.body.stats);
        statements.prepend(msg);

        // 获取代码块
        JCTree.JCBlock body = getBlock(statements);
        methodDecl.body = body;
        this.result = newMethod(methodDecl, body);
    }

    /**
     * 跳过 接口、抽象、无注解 方法
     * @param methodDecl
     * @return
     */
    private boolean isJump(JCTree.JCMethodDecl methodDecl) {
        if ((methodDecl.sym.getEnclosingElement().flags() & Flags.INTERFACE) != 0) {
            return true;
        }
        if ((methodDecl.getModifiers().flags & Flags.ABSTRACT) != 0) {
            return true;
        }
        if (null == methodDecl.sym.getAnnotation(Validation.class)) {
            return true;
        }
        return false;
    }

    /**
     * 获取配置异常
     * @param ms
     * @return
     */
    private String getException(List<Attribute.Compound> ms) {
        for (Attribute.Compound m : ms) {
            if (!m.getAnnotationType().toString().equals(Check.class.getName())) {
                continue;
            }

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : m.getElementValues().entrySet()) {
                if (entry.getKey().toString().equals("invalid()")) {
                    return entry.getValue().getValue().toString();
                }
            }
        }
        return null;
    }

    /**
     * 构建检验逻辑
     * @param statements
     * @param ident
     * @param param
     * @param method
     * @param checkInfo
     * @return
     */
    private void buildLogic(ListBuffer<JCTree.JCStatement> statements,
                                                      JCTree.JCIdent ident,
                                                      JCTree.JCVariableDecl param,
                                                      String method, CheckInfo checkInfo) {
        // 获取参数引用
        JCTree.JCIdent var = treeMaker.Ident(param.name);

        String name = CTreeUtil.getOriginalTypeName(param.sym);
        if(RuleHelper.isPrimitive(name)) {
            return;
        }

        if (!checkInfo.nullable) {
            addNotNullCheck(statements, var, method, checkInfo);
        }

        if (ruleClass.contains(name)) {
            addInvalidStatement(statements, ident, var, method, checkInfo);
        }
    }

    /**
     * 增加校验逻辑
     * @param statements
     * @param ident
     * @param field
     * @param method
     * @param info
     * @return
     */
    private void addInvalidStatement(ListBuffer<JCTree.JCStatement> statements,
                                                               JCTree.JCIdent ident,
                                                               JCTree.JCIdent field, String method, CheckInfo info) {
        // 将校验结果赋值给临时变量
        JCTree.JCExpression expression = getMethod(field, ValidatorTranslator.METHOD_NAME, List.nil());
        JCTree.JCExpressionStatement exec = execMethod(treeMaker.Assign(ident, expression));

        // 抛出异常语句
        JCTree.JCMethodInvocation message = concatStatement(ident, method, info.info);
        JCTree.JCStatement throwStatement = createThrow(message, info.exceptionName);

        // 校验结果
        JCTree.JCExpression condition = treeMaker.Binary(JCTree.Tag.NE, ident, nullNode);
        JCTree.JCIf proc = treeMaker.If(condition, throwStatement, null);

        // 赋值校验子逻辑
        ListBuffer<JCTree.JCStatement> tempStatement = new ListBuffer<>();
        tempStatement.append(exec);
        tempStatement.append(proc);

        if (info.nullable) {
            // 包装判空逻辑
            JCTree.JCExpression nullCheck = treeMaker.Binary(JCTree.Tag.NE, field, nullNode);
            statements.append(treeMaker.If(nullCheck, getBlock(tempStatement), null));
        } else {
            statements.append(getBlock(tempStatement));
        }
    }

    /**
     * 添加非空校验
     * @param statements
     * @param field
     * @param method
     * @param info
     * @return
     */
    private void addNotNullCheck(ListBuffer<JCTree.JCStatement> statements,
                                                           JCTree.JCIdent field, String method, CheckInfo info) {
        JCTree.JCExpression check = treeMaker.Binary(JCTree.Tag.EQ, field, nullNode);

        JCTree.JCMethodInvocation message = concatStatement(treeMaker.Literal(TypeTag.CLASS, field.name + " is null"), method, info.info);
        JCTree.JCStatement throwStatement = createThrow(message, info.exceptionName);

        statements.add(treeMaker.If(check, throwStatement, null));
    }

    /**
     * 并且信息
     * @param info
     * @param method
     * @param message
     * @return
     */
    private JCTree.JCMethodInvocation concatStatement(JCTree.JCExpression info, String method, String message) {
        JCTree.JCExpression args = treeMaker.Literal(message.equals("") ?
                DEFAULT_MESSAGE + method : message + method);
        return getMethod(args, "concat", List.of(info));
    }

    /**
     * 创建新方法
     * @param methodDecl
     * @param body
     * @return
     */
    private JCTree.JCMethodDecl newMethod(JCTree.JCMethodDecl methodDecl, JCTree.JCBlock body) {
        return treeMaker.MethodDef(methodDecl.mods,
                methodDecl.name,
                methodDecl.restype,
                methodDecl.typarams,
                methodDecl.params,
                methodDecl.thrown,
                body, methodDecl.defaultValue);
    }

    /**
     * 检验逻辑
     */
    class CheckInfo {

        private String exceptionName;

        private String info;

        private boolean nullable;

        CheckInfo(String exceptionName, String info, boolean nullable) {
            this.exceptionName = exceptionName;
            this.info = info;
            this.nullable = nullable;
        }
    }
}
