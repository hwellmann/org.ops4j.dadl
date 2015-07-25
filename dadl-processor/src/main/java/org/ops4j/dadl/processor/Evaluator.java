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
 * @author hwellmann
 *
 */
public class Evaluator {

    private List<Object> infoStack;
    private ELProcessor processor;

    public Evaluator() {
        this.infoStack = new ArrayList<>();
        this.processor = new ELProcessor();
        processor.setValue("up", infoStack);
    }

    /**
     * @param info
     */
    public void pushStack(Object info) {
        infoStack.add(0, info);
        processor.setValue("self", info);
    }

    public void popStack() {
        infoStack.remove(0);
        if (!infoStack.isEmpty()) {
            processor.setValue("self", infoStack.get(0));
        }
    }

    public long getExpectedValue(Tag tag) {
        return Long.parseUnsignedLong(tag.getHexValue(), 16);
    }

    public int computeLength(DadlType type) {
        return (Integer) processor.getValue(type.getLength(), Integer.class);
    }

    public void setProperty(String propertyName, Object value) {
        processor.setValue("self." + propertyName, value);
    }

    public Object getProperty(String propertyName) {
        return processor.eval("self." + propertyName);
    }

    @SuppressWarnings("unchecked")
    public <T> T evaluate(String expression, Class<T> klass) {
        return (T) processor.getValue(expression, klass);
    }

    public Object evaluate(String expression) {
        return processor.eval(expression);
    }
}
