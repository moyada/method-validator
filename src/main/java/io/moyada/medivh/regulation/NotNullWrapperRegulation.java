package io.moyada.medivh.regulation;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.ListBuffer;
import io.moyada.medivh.support.ExpressionMaker;
import io.moyada.medivh.util.CTreeUtil;
import io.moyada.medivh.support.TypeTag;

/**
 * 非空包装规则
 * 当对象可能出现 null，并且为进行非空校验处理
 * @author xueyikang
 * @since 1.0
 **/
public class NotNullWrapperRegulation implements Regulation {

    @Override
    public ListBuffer<JCTree.JCStatement> handle(ExpressionMaker expressionMaker, ListBuffer<JCTree.JCStatement> statements,
                                                 String fieldName, JCTree.JCExpression self, JCTree.JCStatement action) {
        TreeMaker treeMaker = expressionMaker.getTreeMaker();

        // 对象不为空判断
        JCTree.JCExpression condition = CTreeUtil.newBinary(treeMaker, TypeTag.NE, self, expressionMaker.nullNode);

        // 包裹当前语句构建
        JCTree.JCIf exec = treeMaker.If(condition, treeMaker.Block(0, statements.toList()), null);

        ListBuffer<JCTree.JCStatement> jcStatements = CTreeUtil.newStatement();
        jcStatements.append(exec);
        return jcStatements;
    }
}
