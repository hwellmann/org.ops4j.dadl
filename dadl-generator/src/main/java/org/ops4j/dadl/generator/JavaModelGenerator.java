/*
 * Copyright 2015 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.dadl.generator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.EnumerationElement;
import org.ops4j.dadl.metamodel.gen.Model;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.TaggedSequence;
import org.ops4j.dadl.model.ValidatedModel;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.codemodel.writer.FileCodeWriter;

/**
 * Generates Java source files from a given model. There will be one POJO for each complex type and
 * one Java enum for each enumeration type.
 * <p>
 * The source will be output when the {@link #generateJavaModel()} method is called.
 *
 * @author hwellmann
 *
 */
public class JavaModelGenerator {

    private static final String VALUE = "value";
    private ValidatedModel model;
    private String packageName;
    private Path outputDir;
    private JCodeModel codeModel;
    private JPackage pkg;

    /**
     * Generates Java sources from a given validated model. The classes will be written to a
     * subdirectory given output root directory, corresponding to the package name, when the
     * {@link #generateJavaModel()} method is called. E.g. for output directory {@code target} and
     * package name {@code com.example.foo}, the generated files will be written to
     * {@code target/com/example/foo}. Any required directories will be created if needed.
     *
     * @param model
     *            validated model
     * @param packageName
     *            fully qualified package name for all generated classes
     * @param outputDir
     *            output root directory for the generated classes.
     */
    public JavaModelGenerator(ValidatedModel model, String packageName, Path outputDir) {
        this.model = model;
        this.packageName = packageName;
        this.outputDir = outputDir;
    }

    /**
     * Generates the Java model classes.
     *
     * @throws IOException on write error
     */
    public void generateJavaModel() throws IOException {
        codeModel = new JCodeModel();
        pkg = codeModel._package(packageName);

        model.getTypeMap().forEach((n, type) -> createType(type));

        Model rawModel = model.getModel();
        rawModel.getEnumeration().stream().forEach(e -> fillEnumeration(e));
        rawModel.getTaggedSequence().stream().forEach(s -> fillTaggedSequencePojo(s));
        rawModel.getSequence().stream().forEach(s -> fillSequencePojo(s));
        rawModel.getChoice().stream().forEach(s -> fillChoicePojo(s));

        File dir = outputDir.toFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new IOException("could not create " + dir);
            }
        }
        codeModel.build(new FileCodeWriter(dir));
    }

    private void createType(Object type) {
        try {
            JDefinedClass klass = null;
            if (type instanceof Sequence) {
                Sequence sequence = (Sequence) type;
                klass = pkg._class(sequence.getName());
            }
            else if (type instanceof TaggedSequence) {
                TaggedSequence sequence = (TaggedSequence) type;
                klass = pkg._class(sequence.getName());
            }
            else if (type instanceof Choice) {
                Choice choice = (Choice) type;
                klass = pkg._class(choice.getName());
            }
            else if (type instanceof Enumeration) {
                Enumeration enumeration = (Enumeration) type;
                klass = pkg._enum(enumeration.getName());
            }
            if (klass != null) {
                klass.annotate(Generated.class).param(VALUE, getClass().getName())
                    .param("date", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            }
        }
        catch (JClassAlreadyExistsException exc) {
            throw new IllegalStateException(exc);
        }
    }

    private void fillEnumeration(Enumeration enumeration) {
        JDefinedClass klass = pkg._getClass(enumeration.getName());
        JType jtype = getJavaType(enumeration).boxify();
        enumeration.getElement().forEach(e -> generateEnumerationElement(klass, e));
        generateEnumerationFieldAndGetter(klass, jtype);
        generateEnumerationConstructor(klass, jtype);
        generateEnumerationFromValueMethod(klass, jtype);
    }

    private void generateEnumerationFromValueMethod(JDefinedClass klass, JType jtype) {
        JMethod method = klass.method(JMod.PUBLIC | JMod.STATIC, klass, "fromValue");
        JVar valueParam = method.param(jtype, VALUE);
        JBlock methodBody = method.body();
        JBlock foreachBody = methodBody.forEach(klass, "v", klass.staticInvoke("values")).body();
        JFieldRef vRef = JExpr.ref("v");
        foreachBody._if(vRef.invoke("getValue").invoke("equals").arg(valueParam))
            ._then()._return(vRef);
        methodBody._return(JExpr._null());
    }

    private void generateEnumerationConstructor(JDefinedClass klass, JType jtype) {
        JMethod constructor = klass.constructor(JMod.PRIVATE);
        JVar param = constructor.param(jtype, VALUE);
        constructor.body().assign(JExpr._this().ref(VALUE), param);
    }

    private void generateEnumerationFieldAndGetter(JDefinedClass klass, JType jtype) {
        JFieldVar field = klass.field(JMod.PRIVATE, jtype, VALUE);

        JMethod getter = klass.method(JMod.PUBLIC, jtype, getGetterName(VALUE));
        getter.body()._return(field);
    }

    private void fillSequencePojo(Sequence sequence) {
        JDefinedClass klass = pkg._getClass(sequence.getName());
        sequence.getElement().forEach(e -> generateSequenceFieldAndAccessors(klass, e));
    }

    private void fillTaggedSequencePojo(TaggedSequence sequence) {
        JDefinedClass klass = pkg._getClass(sequence.getName());
        sequence.getElement().forEach(e -> generateSequenceFieldAndAccessors(klass, e));
    }

    private void fillChoicePojo(Choice choice) {
        JDefinedClass klass = pkg._getClass(choice.getName());
        choice.getElement().forEach(e -> generateFieldAndAccessors(klass, e));
    }

    private void generateEnumerationElement(JDefinedClass klass, EnumerationElement element) {
        JExpression expr = null;
        if (element.getDecValue() != null) {
            expr = JExpr.lit(element.getDecValue());
        }
        else if (element.getHexValue() != null) {
            expr = JExpr.lit(Integer.parseInt(element.getHexValue(), 16));
        }
        else if (element.getValue() != null) {
            expr = JExpr.lit(element.getValue());
        }
        klass.enumConstant(element.getName().toUpperCase()).arg(expr);
    }

    private void generateSequenceFieldAndAccessors(JDefinedClass klass, SequenceElement element) {
        if (model.isList(element)) {
            generateListFieldAndAccessors(klass, element);
        }
        else {
            generateFieldAndAccessors(klass, element);
        }
    }

    private void generateListFieldAndAccessors(JDefinedClass klass, SequenceElement element) {
        String fieldName = element.getName();
        JType elementType = getJavaType(element);
        JClass listType = codeModel.ref(List.class).narrow(elementType);
        JFieldVar field = klass.field(JMod.PRIVATE, listType, fieldName);

        JMethod getter = klass.method(JMod.PUBLIC, listType, getGetterName(fieldName));
        getter.body()._if(field.eq(JExpr._null()))._then()
            .assign(field, JExpr._new(codeModel.ref(ArrayList.class).narrow(elementType)));
        getter.body()._return(field);
    }

    private void generateFieldAndAccessors(JDefinedClass klass, Element element) {
        String fieldName = element.getName();
        JType jtype = getJavaType(element);
        JFieldVar field = klass.field(JMod.PRIVATE, jtype, fieldName);

        JMethod getter = klass.method(JMod.PUBLIC, jtype, getGetterName(fieldName));
        getter.body()._return(field);

        JMethod setter = klass.method(JMod.PUBLIC, codeModel.VOID, getSetterName(fieldName));
        JVar p1 = setter.param(jtype, fieldName);
        setter.body().assign(JExpr._this().ref(fieldName), p1);
    }

    private JType getJavaType(Element element) {
        DadlType type = model.getType(element.getType());
        JType jtype = null;
        if (type instanceof Enumeration) {
            jtype = codeModel._getClass(String.format("%s.%s", packageName, type.getName()));
        }
        else if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            jtype = getJavaType(simpleType);
        }
        else {
            jtype = codeModel._getClass(String.format("%s.%s", packageName, type.getName()));
        }
        return jtype;
    }

    private JType getJavaType(SimpleType simpleType) {
        try {
            return codeModel.parseType(simpleType.getMappedType());
        }
        catch (ClassNotFoundException exc) {
            throw new IllegalArgumentException(simpleType.getMappedType(), exc);
        }
    }

    private String getGetterName(String fieldName) {
        return getAccessorName("get", fieldName);
    }

    private String getSetterName(String fieldName) {
        return getAccessorName("set", fieldName);
    }

    private String getAccessorName(String prefix, String fieldName) {
        StringBuilder buffer = new StringBuilder(prefix);
        buffer.append(fieldName.substring(0, 1).toUpperCase());
        buffer.append(fieldName.substring(1));
        return buffer.toString();
    }
}
