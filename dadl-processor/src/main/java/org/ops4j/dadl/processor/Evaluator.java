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
package org.ops4j.dadl.processor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.el.ELProcessor;

import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Discriminator;
import org.ops4j.dadl.metamodel.gen.Tag;
import org.ops4j.dadl.metamodel.gen.TestKind;

/**
 * Wraps an Expression Language processor and maintains a stack of Java model objects. The stack
 * corresponds to the current hierarchy of types and instances processed by a DADL processor.
 * <p>
 * The current object is stored in a variable named {@code self}. The list of parent objects of the
 * current object is stored in a list variable named {@code up} such that {@code up[0]} is
 * {@code self}, {@code up[1]} is the parent of the current object, {@code up[2]} is the parent of
 * the parent, and so on.
 *
 * @author hwellmann
 *
 */
public class Evaluator {

    private List<Object> infoStack;
    private ELProcessor processor;

    /**
     * Creates an empty evaluator. {@code self} is undefined an {@code up} is empty.
     */
    public Evaluator() {
        this.infoStack = new ArrayList<>();
        this.processor = new ELProcessor();
        processor.setValue("up", infoStack);
        pushStack();
    }

    /**
     * Pushes the given info object onto the stack. The given object is inserted into the {@code up}
     * list at index 0.
     *
     * @param info
     *            Java model object
     */
    public void pushStack(Object info) {
        infoStack.add(0, info);
        processor.setValue("self", info);
    }

    /**
     * Pushes the stack, leaving the top element {@code self} undefined.
     */
    public void pushStack() {
        infoStack.add(0, null);
        processor.setValue("self", null);
    }

    public void setSelf(Object info) {
        infoStack.set(0, info);
        processor.setValue("self", info);
    }

    public Object setSelfEnumeration(Object info, Class<?> klass) {
        try {
            Method method = getMethod("fromValue", klass);
            processor.defineFunction("", "fromValue", method);
            processor.setValue("info", info);
            Object enumValue = processor.eval("fromValue(info)");
            processor.setValue("info", enumValue);
            infoStack.set(0, enumValue);
            return enumValue;
        }
        catch (NoSuchMethodException exc) {
            throw new UnmarshalException(exc);
        }
    }

    public Object getEnumerationValue(Object info) {
        processor.setValue("info", info);
        return processor.eval("info.value");
    }

    /**
     * @param name
     * @param klass
     * @return
     */
    private Method getMethod(String name, Class<?> klass) {
        for (Method method : klass.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * Pops the stack. The element at index 0 is removed from the {@code up} list.
     *
     * @param info
     *            Java model object
     */
    public void popStack() {
        infoStack.remove(0);
        if (!infoStack.isEmpty()) {
            processor.setValue("self", infoStack.get(0));
        }
    }

    /**
     * Gets the expected value of a tag.
     *
     * @param tag
     *            tag model
     * @return tag value
     */
    public long getExpectedValue(Tag tag) {
        return Long.parseUnsignedLong(tag.getHexValue(), 16);
    }

    /**
     * Computes the actual length of a type by evaluating its {@code length} expression (which may
     * be a literal number).
     *
     * @param type
     *            type with explicit length
     * @return length
     */
    public int computeLength(DadlType type) {
        return (Integer) processor.getValue(type.getLength(), Integer.class);
    }

    /**
     * Computes the mininum length of a type by evaluating its {@code minLength} expression (which may
     * be a literal number).
     *
     * @param type
     *            type with minimum length
     * @return length
     */
    public int computeMinLength(DadlType type) {
        return (Integer) processor.getValue(type.getMinLength(), Integer.class);
    }

    /**
     * Sets the property with the given name of the current object's parent to the given value.
     *
     * @param propertyName
     *            property name
     * @param value
     *            value to be set
     */
    public void setParentProperty(String propertyName, Object value) {
        processor.setValue("up[1]." + propertyName, value);
    }

    /**
     * Gets the property with the given name of the current object's parent.
     *
     * @param propertyName
     *            property name
     * @return property value
     */
    public Object getParentProperty(String propertyName) {
        return processor.eval("up[1]." + propertyName);
    }

    /**
     * Evaluates the given expression, coercing the value to the given class.
     *
     * @param expression
     *            EL expression
     * @param klass
     *            expected class of result
     * @return evaluation result
     */
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String expression, Class<T> klass) {
        return (T) processor.getValue(expression, klass);
    }

    /**
     * Evaluates the given expression.
     *
     * @param expression
     *            EL expression
     * @return evaluation result
     */
    public Object evaluate(String expression) {
        return processor.eval(expression);
    }

    /**
     * Sets the variable with the given name to the given value.
     *
     * @param variableName
     *            variable name
     * @param value
     *            variable value
     */
    public void setVariable(String variableName, Object value) {
        processor.setValue(variableName, value);
    }

    /**
     * Clears the variable with the given name.
     *
     * @param variableName
     *            variable name
     */
    public void clearVariable(String variableName) {
        processor.setValue(variableName, null);
    }

    @Override
    public String toString() {
        return infoStack.toString();
    }

    void checkDiscriminator(Object value, DadlType type) {
        if (type == null) {
            return;
        }
        Discriminator discriminator = type.getDiscriminator();
        if (discriminator == null) {
            return;
        }
        if (discriminator.getTestKind() == TestKind.PATTERN) {
            throw new UnsupportedOperationException(discriminator.getTestKind().toString());
        }
        String test = discriminator.getTest();
        boolean satisfied = evaluate(test, Boolean.class);
        if (!satisfied) {
            String msg = discriminator.getMessage();
            if (msg == null) {
                msg = String.format("%s not satisfied on %s", test, type.getName());
            }
            throw new AssertionError(msg);
        }
    }
}
