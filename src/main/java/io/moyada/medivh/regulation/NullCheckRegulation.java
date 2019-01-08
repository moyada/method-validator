package io.moyada.medivh.regulation;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.ListBuffer;
import io.moyada.medivh.support.ExpressionMaker;
import io.moyada.medivh.util.CTreeUtil;
import io.moyada.medivh.support.ElementOptions;
import io.moyada.medivh.support.TypeTag;

/**
 * 非空校验规则
 * 默认规则，针对对象判空处理，可以使用 {@link io.moyada.medivh.annotation.Nullable} 取消规则
 * @author xueyikang
 * @since 1.0
 **/
public class NullCheckRegulation extends BaseRegulation implements Regulation {

    @Override
    public ListBuffer<JCTree.JCStatement> handle(ExpressionMaker expressionMaker, ListBuffer<JCTree.JCStatement> statements,
                                                 String fieldName, JCTree.JCExpression self, JCTree.JCStatement action) {
        if (null == action) {
            action = createAction(expressionMaker, buildInfo(fieldName));
        }
        JCTree.JCStatement exec = doHandle(expressionMaker, statements, self, action);
        // 将非空处理提前
        statements.prepend(exec);
        return statements;
    }

    @Override
    JCTree.JCStatement doHandle(ExpressionMaker expressionMaker, ListBuffer<JCTree.JCStatement> statements,
                                JCTree.JCExpression self, JCTree.JCStatement action) {
        TreeMaker treeMaker = expressionMaker.getTreeMaker();
        // 等于 null 执行动作
        JCTree.JCExpression condition = CTreeUtil.newBinary(treeMaker, TypeTag.EQ, self, expressionMaker.nullNode);
        return treeMaker.If(condition, action, null);
    }

    @Override
    String buildInfo(String fieldName) {
        return fieldName + " " + ElementOptions.NULL_INFO;
    }
}
