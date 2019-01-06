package io.moyada.medivh.processor;


import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import io.moyada.medivh.annotation.*;
import io.moyada.medivh.core.MakerContext;
import io.moyada.medivh.translator.UtilMethodTranslator;
import io.moyada.medivh.translator.ValidationTranslator;
import io.moyada.medivh.translator.VerificationTranslator;
import io.moyada.medivh.util.CheckUtil;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 校验注解处理器
 * @author xueyikang
 * @since 0.0.1
 **/
public class ValidationProcessor extends AbstractProcessor {

    private Messager messager;

    private Context context;
    private Trees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.trees = Trees.instance(processingEnv);
        this.messager = processingEnv.getMessager();

        messager.printMessage(Diagnostic.Kind.NOTE, "start validate processor");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> rootElements = roundEnv.getRootElements();
        // 获取校验方法
        Collection<? extends Element> methods = getMethods(rootElements,
                Throw.class.getName(), Return.class.getName());

        if (methods.isEmpty()) {
            return true;
        }

        // 获取对象规则
        Collection<? extends Element> rules = getRules(roundEnv,
                Nullable.class, NotNull.class, NumberRule.class, SizeRule.class);
        // 解析聚合类
        Collection<? extends Element> ruleClass = getClass(rules);

        MakerContext makerContext = MakerContext.newInstance(context);

        createUtilMethod(rootElements, makerContext);

        // 校验方法生成器
        TreeTranslator translator = new ValidationTranslator(makerContext, messager);
        for (Element element : ruleClass) {
            JCTree tree = (JCTree) trees.getTree(element);
            tree.accept(translator);
        }

        // 校验逻辑生成器
        translator = new VerificationTranslator(makerContext, messager);
        for (Element element : methods) {
            JCTree tree = (JCTree) trees.getTree(element);
            tree.accept(translator);
        }

        return true;
    }

    /**
     * 获取待增强方法
     * @param rootElements
     * @param annoNames
     * @return
     */
    private Collection<? extends Element> getMethods(Set<? extends Element> rootElements, String... annoNames) {
        Set<Element> methods = new HashSet<Element>();

        boolean filter;
        for (Element rootElement : rootElements) {
            if (rootElement.getKind() == ElementKind.INTERFACE) {
                continue;
            }
            JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) trees.getTree(rootElement);
            boolean checkClass = CheckUtil.isCheckClass(classDecl);

            List<? extends Element> elements = rootElement.getEnclosedElements();
            for (Element element : elements) {
                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }
                JCTree.JCMethodDecl methodDecl = (JCTree.JCMethodDecl) trees.getTree(element);
                // 无需校验的方法
                if (isJump(methodDecl)) {
                    continue;
                }
                // 无参数方法
                com.sun.tools.javac.util.List<JCTree.JCVariableDecl> parameters = methodDecl.getParameters();
                if (parameters.isEmpty()) {
                    continue;
                }

                // 标记排除
                if (CheckUtil.isExclusive(methodDecl.sym)) {
                    continue;
                }

                // 类上或方法上标记校验处理
                if (checkClass || CheckUtil.isCheckMethod(methodDecl)) {
                    methods.add(element);
                    continue;
                }

                filter = true;
                for (int i = 0; filter && i < parameters.size(); i++) {
                    JCTree.JCVariableDecl variableDecl = parameters.get(i);
                    List<? extends AnnotationMirror> mirrors = variableDecl.sym.getAnnotationMirrors();
                    if (mirrors.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; filter && j < mirrors.size(); j++) {
                        String name = mirrors.get(j).getAnnotationType().toString();
                        for (int k = 0; filter && k < annoNames.length; k++) {
                            // 存在标记注解的字段，则将该方法记录，跳至下一方法
                            if (annoNames[k].equals(name)) {
                                methods.add(element);
                                filter = false;
                            }
                        }
                    }
                }
            }
        }

        return methods;
    }

    /**
     * 创建工具方法
     * @param elements
     * @param makerContext
     */
    private void createUtilMethod(Collection<? extends Element> elements, MakerContext makerContext) {
        List<TypeElement> typeElements = ElementFilter.typesIn(elements);
        if (typeElements.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "cannot find any class type");
            return;
        }
        TypeElement classElement = null;
        for (TypeElement element : typeElements) {
            ElementKind kind = element.getKind();
            if (kind != ElementKind.CLASS) {
                continue;
            }
            if (element.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
                continue;
            }
            if (!isPublic(element)) {
                continue;
            }

            classElement = element;
        }

        if (classElement == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "cannot find any public class");
            return;
        }

        JCTree tree = (JCTree) trees.getTree(classElement);
        tree.accept(new UtilMethodTranslator(makerContext, messager, classElement.toString()));
    }

    /**
     * 是否是public类型
     * @param element
     * @return
     */
    private boolean isPublic(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        for (Modifier modifier : modifiers) {
            if (modifier == Modifier.PUBLIC) {
                return true;
            }
        }

        return false;
    }

    /**
     * 跳过 接口、抽象、无注解 方法
     * @param methodDecl
     * @return
     */
    private boolean isJump(JCTree.JCMethodDecl methodDecl) {
        if (null == methodDecl) {
            return true;
        }
        if ((methodDecl.sym.getEnclosingElement().flags() & Flags.INTERFACE) != 0) {
            return true;
        }
        if ((methodDecl.getModifiers().flags & Flags.ABSTRACT) != 0) {
            return true;
        }
        return false;
    }

    private Collection<? extends Element> getRules(RoundEnvironment roundEnv, Class<? extends Annotation>... annos) {
        Set<Element> rules = new HashSet<Element>();

        Set<? extends Element> elements;

        for (Class<? extends Annotation> anno : annos) {
            elements = roundEnv.getElementsAnnotatedWith(anno);
            rules.addAll(ElementFilter.fieldsIn(elements));
            rules.addAll(ElementFilter.methodsIn(elements));
        }

        elements = roundEnv.getElementsAnnotatedWith(NotBlank.class);
        if (elements.isEmpty()) {
            return rules;
        }

        rules.addAll(ElementFilter.fieldsIn(elements));
        rules.addAll(ElementFilter.methodsIn(elements));

//        try {
//            SystemUtil.createFile(processingEnv.getFiler(), io.moyada.medivh.core.Element.BLANK_METHOD[0]);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        return rules;
    }

    /**
     * 获取规则属性所属的类集合
     * @param rules
     * @return
     */
    private Collection<? extends Element> getClass(Collection<? extends Element> rules) {
        Set<Element> classRule = new HashSet<Element>();

        for (Element element : rules) {
            classRule.add( element.getEnclosingElement());
        }
        return classRule;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new HashSet<String>(8, 1.0F);
        annotationTypes.add(Throw.class.getName());
        annotationTypes.add(Return.class.getName());
        annotationTypes.add(Nullable.class.getName());
        annotationTypes.add(NotNull.class.getName());
        annotationTypes.add(NotBlank.class.getName());
        annotationTypes.add(NumberRule.class.getName());
        annotationTypes.add(SizeRule.class.getName());
        annotationTypes.add(Variable.class.getName());
        return annotationTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        if (SourceVersion.latest().compareTo(SourceVersion.RELEASE_6) > 0) {
            return SourceVersion.latest();
        } else {
            return SourceVersion.RELEASE_6;
        }
    }
}
