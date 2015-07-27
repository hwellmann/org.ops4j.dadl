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
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.model.ValidatedModel;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.codemodel.writer.FileCodeWriter;

/**
 * Generates Java source files from a given model. The will be one POJO for each complex type. The
 * source will be output when the {@link #generateJavaModel()} method is called.
 *
 * @author hwellmann
 *
 */
public class JavaModelGenerator {

    private ValidatedModel model;
    private String packageName;
    private Path outputDir;
    private JCodeModel codeModel;
    private JPackage pkg;

    /**
     * Generates Java sources from a given validated model. The classes will be written to a
     * subdirectory given output root directory, corresponding to the package name, when the
     * {@link #generateJavaModel()} method is called. E.g. for output directory {@target} and
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

        model.getModel().getSequence().stream().forEach(s -> fillSequencePojo(s));
        model.getModel().getChoice().stream().forEach(s -> fillChoicePojo(s));

        outputDir.toFile().mkdirs();
        codeModel.build(new FileCodeWriter(outputDir.toFile()));
    }

    /**
     * @param type
     * @return
     * @throws JClassAlreadyExistsException
     */
    private void createType(Object type) {
        try {
            JDefinedClass klass = null;
            if (type instanceof Sequence) {
                Sequence sequence = (Sequence) type;
                klass = pkg._class(sequence.getName());
            }
            else if (type instanceof Choice) {
                Choice choice = (Choice) type;
                klass = pkg._class(choice.getName());
            }
            if (klass != null) {
                klass.annotate(Generated.class).param("value", getClass().getName())
                    .param("date", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            }
        }
        catch (JClassAlreadyExistsException exc) {
            throw new IllegalStateException(exc);
        }
    }

    /**
     * @param s
     * @return
     */
    private void fillSequencePojo(Sequence sequence) {
        JDefinedClass klass = pkg._getClass(sequence.getName());
        sequence.getElement().forEach(e -> generateSequenceFieldAndAccessors(klass, e));
    }

    private void fillChoicePojo(Choice choice) {
        JDefinedClass klass = pkg._getClass(choice.getName());
        choice.getElement().forEach(e -> generateFieldAndAccessors(klass, e));
    }

    private void generateSequenceFieldAndAccessors(JDefinedClass klass, SequenceElement element) {
        if (model.isList(element)) {
            generateListFieldAndAccessors(klass, element);
        }
        else {
            generateFieldAndAccessors(klass, element);
        }
    }

    /**
     * @param klass
     * @param element
     */
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

    /**
     * @param type
     * @return
     */
    private JType getJavaType(Element element) {
        DadlType type = model.getType(element.getType());
        JType jtype = null;
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            jtype = getJavaType(simpleType);
        }
        else {
            jtype = codeModel._getClass(String.format("%s.%s", packageName, type.getName()));
        }
        return jtype;
    }

    /**
     * @param simpleType
     * @return
     */
    private JType getJavaType(SimpleType simpleType) {
        switch (simpleType.getJavaType()) {
            case "boolean":
                return codeModel.BOOLEAN;
            case "byte":
                return codeModel.BYTE;
            case "short":
                return codeModel.SHORT;
            case "int":
                return codeModel.INT;
            case "long":
                return codeModel.LONG;
            default:
                return codeModel.ref(simpleType.getJavaType());
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
