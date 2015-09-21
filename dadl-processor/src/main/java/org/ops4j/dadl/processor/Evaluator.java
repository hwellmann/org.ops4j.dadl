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

import static org.ops4j.dadl.io.Constants.HEX_BASE;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.el.ELProcessor;
import javax.el.PropertyNotFoundException;

import org.ops4j.dadl.exc.DadlException;
import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.io.Constants;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Discriminator;
import org.ops4j.dadl.metamodel.gen.LengthKind;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
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
public final class Evaluator {

    public static final String UP = "up";
    public static final String SELF = "self";

    private List<Object> infoStack;
    private ELProcessor processor;
    private List<ELProcessor> processorStack;

    /**
     * Creates an empty evaluator. {@code self} is undefined an {@code up} is empty.
     */
    public Evaluator() {
        this.infoStack = new ArrayList<>();
        this.processorStack = new ArrayList<>();
        this.processor = new ELProcessor();
        processor.setValue(UP, infoStack);
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
        processor = new ELProcessor();
        processor.setValue(SELF, info);
        processorStack.add(0, processor);
    }

    /**
     * Pushes the stack, leaving the top element {@code self} undefined.
     */
    public void pushStack() {
        infoStack.add(0, null);
        this.processor = new ELProcessor();
        processor.setValue(UP, infoStack);
        processor.setValue(SELF, null);
        processorStack.add(0, processor);
    }

    /**
     * Pushes the given object onto the stack, and updates {@code self} to point to the top element.
     *
     * @param info
     *            object to be pushed
     */
    public void setSelf(Object info) {
        infoStack.set(0, info);
        processor.setValue(SELF, info);
    }

    /**
     * Takes a value of the given enumeration type, converts it to its Java model representation and
     * pushes the result onto the stack, updating {@code self}.
     *
     * @param info
     *            object to be pushed
     * @param klass
     *            Java model class for a DADL enumeration type
     * @return Java enumeration value
     */
    public Object setSelfEnumeration(Object info, Class<?> klass) {
        try {
            Method method = getMethod("fromValue", klass);
            processor.defineFunction("", "fromValue", method);
            processor.setValue(SELF, info);
            Object enumValue = processor.eval("fromValue(self)");
            processor.setValue(SELF, enumValue);
            infoStack.set(0, enumValue);
            return enumValue;
        }
        catch (NoSuchMethodException exc) {
            throw new UnmarshalException(exc);
        }
    }

    /**
     * Take a Java enumeration value and converts it to its DADL representation.
     *
     * @param info
     *            value of enumeration class generated from a DADL enumeration type
     * @return content value
     */
    public Object getEnumerationValue(Object info) {
        processor.setValue("$enum", info);
        return processor.eval("$enum.value");
    }

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
     */
    public void popStack() {
        infoStack.remove(0);
        processorStack.remove(0);
        if (!infoStack.isEmpty()) {
            processor = processorStack.get(0);
            processor.setValue(SELF, infoStack.get(0));
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
        return Long.parseUnsignedLong(tag.getHexValue(), HEX_BASE);
    }

    /**
     * Computes the actual length of a type by evaluating its {@code length} expression (which may
     * be a literal number).
     *
     * @param type
     *            type with explicit length
     * @return length
     */
    public Integer computeLength(DadlType type) {

        if (type.getLengthKind() == LengthKind.END_OF_PARENT) {
            return null;
        }
        else {
            return (Integer) processor.getValue(type.getLength(), Integer.class);
        }
    }

    public int computeBitLength(DadlType type, long bitPosition) {

        if (type.getLengthKind() == LengthKind.END_OF_PARENT) {
            for (ELProcessor proc : processorStack) {
                try {
                    Long end = (Long) proc.getValue("$end", Long.class);
                    return (int) (end - bitPosition);
                }
                catch (PropertyNotFoundException exc) {
                    // ignore
                }
            }
            throw new DadlException("cannot determine endOfParent");
        }
        else {
            int length = (Integer) processor.getValue(type.getLength(), Integer.class);
            if (type.getLengthUnit() == LengthUnit.BYTE) {
                length *= Constants.BYTE_SIZE;
            }
            return length;
        }
    }

    public long getEndOfParent() {
        for (ELProcessor proc : processorStack) {
            try {
                Long end = (Long) proc.getValue("$end", Long.class);
                return end;
            }
            catch (PropertyNotFoundException exc) {
                // ignore
            }
        }
        throw new DadlException("cannot determine endOfParent");
    }

    /**
     * Computes the mininum length of a type by evaluating its {@code minLength} expression (which
     * may be a literal number).
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

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String variableName, Class<T> klass) {
        try {
            return (T) processor.getValue(variableName, klass);
        }
        catch (PropertyNotFoundException exc) {
            return null;
        }
    }



    @Override
    public String toString() {
        return infoStack.toString();
    }

    void checkDiscriminator(DadlType type) {
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
