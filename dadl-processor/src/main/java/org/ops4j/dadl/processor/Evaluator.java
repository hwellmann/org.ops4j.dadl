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

import java.util.ArrayList;
import java.util.List;

import javax.el.ELProcessor;

import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Tag;

/**
 * Wraps an Expression Language processor and maintains a stack of Java model objects. The stack
 * corrsponds to the current hierarchy of types and instances processed by a DADL processor.
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
     * Sets the property with the given name of the current object to the given value.
     *
     * @param propertyName
     *            property name
     * @param value
     *            value to be set
     */
    public void setProperty(String propertyName, Object value) {
        processor.setValue("self." + propertyName, value);
    }

    /**
     * Gets the property with the given name of the current object.
     *
     * @param propertyName
     *            property name
     * @return property value
     */
    public Object getProperty(String propertyName) {
        return processor.eval("self." + propertyName);
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
}
