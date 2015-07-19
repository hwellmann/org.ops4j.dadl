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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import javax.el.ELProcessor;

import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.io.ByteArrayBitStreamReader;
import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.LengthField;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.Tag;
import org.ops4j.dadl.model.ValidatedModel;

/**
 * @author hwellmann
 *
 */
public class Unmarshaller {

    private DadlContext context;
    private ValidatedModel model;
    private List<Object> infoStack;
    private ELProcessor processor;

    /**
     * 
     */
    public Unmarshaller(DadlContext context, ValidatedModel model) {
        this.context = context;
        this.model = model;
        this.infoStack = new ArrayList<>();
        this.processor = new ELProcessor();
        processor.setValue("up", infoStack);
    }

    public <T> T unmarshal(byte[] bytes, Class<T> klass) throws IOException {
        String typeName = klass.getSimpleName();
        DadlType type = model.getType(typeName);
        try (BitStreamReader reader = new ByteArrayBitStreamReader(bytes)) {
            return unmarshal(type, klass, reader);
        }
    }

    private <T> T unmarshal(DadlType type, Class<T> klass, BitStreamReader reader)
        throws IOException {
        T info = readValueViaAdapter(type, klass, reader);
        if (info != null) {
            return info;
        }
        info = newInstance(klass);
        pushStack(info);
        try {
            if (type instanceof Sequence) {
                return unmarshalSequence(info, (Sequence) type, klass, reader);
            }
            else if (type instanceof Choice) {
                return unmarshalChoice(info, (Choice) type, klass, reader);
            }
            else {
                throw new UnmarshalException("cannot unmarshal type " + klass.getName());
            }
        }
        finally {
            popStack();
        }
    }

    /**
     * @param info
     */
    private void pushStack(Object info) {
        infoStack.add(0, info);
        processor.setValue("self", info);
    }

    /**
     * 
     */
    private void popStack() {
        infoStack.remove(0);
        if (!infoStack.isEmpty()) {
            processor.setValue("self", infoStack.get(0));
        }
    }

    private <T> T newInstance(Class<T> klass) {
        try {
            T info = klass.newInstance();
            return info;
        }
        catch (InstantiationException | IllegalAccessException exc) {
            throw new UnmarshalException("cannot instantiate " + klass.getName());
        }
    }

    private <T> T unmarshalSequence(T info, Sequence sequence, Class<T> klass,
        BitStreamReader reader)
            throws IOException {
        Tag tag = sequence.getTag();
        if (tag != null) {
            unmarshalTag(tag, reader);
        }
        LengthField lengthField = sequence.getLength();
        if (lengthField != null) {
            unmarshalLengthField(lengthField, reader);
        }
        for (SequenceElement element : sequence.getElement()) {
            unmarshalSequenceField(info, klass, element, reader);
        }
        return info;
    }

    /**
     * @param tag
     * @param reader
     * @throws IOException
     */
    private void unmarshalTag(Tag tag, BitStreamReader reader) throws IOException {
        String typeName = tag.getType();
        Object type = model.getType(typeName);
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            long actualTag = readSimpleValue(simpleType, Long.class, reader);
            long expectedTag = getExpectedValue(tag);
            if (actualTag != expectedTag) {
                String msg = String.format("tag mismatch: actual = %X, expected = %X",
                    actualTag, expectedTag);
                throw new AssertionError(msg);
            }
        }
        else {
            throw new UnmarshalException("tag type is not a simple type: " + typeName);
        }
    }

    private long unmarshalLengthField(LengthField lengthField, BitStreamReader reader)
        throws IOException {
        DadlType type = model.getType(lengthField.getType());
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            return readSimpleValue(simpleType, Long.class, reader);
        }
        throw new UnmarshalException("length field must have simple type");
    }

    /**
     * @param tag
     * @return
     */
    private long getExpectedValue(Tag tag) {
        return Long.parseUnsignedLong(tag.getHexValue(), 16);
    }

    private void unmarshalSequenceField(Object info, Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        try {
            Field field = klass.getDeclaredField(element.getName());
            if (model.isList(element)) {
                ParameterizedType type = (ParameterizedType) field.getGenericType();
                Class<?> elementClass = (Class<?>) type.getActualTypeArguments()[0];
                unmarshalSequenceListField(info, elementClass, element, reader);
            }
            else {
                Object fieldValue = unmarshalSequenceIndividualField(field.getType(), element,
                    reader);
                processor.setValue("self." + element.getName(), fieldValue);
                // checkAssertion(info, field);
            }
        }
        catch (NoSuchFieldException | SecurityException exc) {
            // TODO Auto-generated catch block
            exc.printStackTrace();
        }
    }

    /**
     * @param info
     * @param klass
     * @param element
     * @param reader
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private void unmarshalSequenceListField(Object info, Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        Long numItems = (Long) processor.getValue(element.getOccursCount(), Long.class);
        List<Object> list = (List<Object>) processor.eval("self." + element.getName());
        for (long i = 0; i < numItems; i++) {
            Object fieldValue = unmarshalSequenceIndividualField(klass, element, reader);
            list.add(fieldValue);
        }
    }

    private Object unmarshalSequenceIndividualField(Class<?> klass, Element element,
        BitStreamReader reader) throws IOException {
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof SimpleType) {
            return readSimpleValue((SimpleType) fieldType, klass, reader);
        }
        else {
            return unmarshal(fieldType, klass, reader);
        }
    }

    private <T> T unmarshalChoice(T info, Choice choice, Class<T> klass, BitStreamReader reader) {
        boolean branchMatched = false;
        for (Element element : choice.getElement()) {
            reader.mark();
            try {
                String fieldName = element.getName();
                Field field = klass.getDeclaredField(fieldName);
                DadlType fieldType = model.getType(element.getType());

                Object fieldValue;
                if (fieldType instanceof SimpleType) {
                    fieldValue = readSimpleValue((SimpleType) fieldType, field.getType(), reader);
                }
                else {
                    fieldValue = unmarshal(fieldType, field.getType(), reader);
                }
                processor.setValue("self." + fieldName, fieldValue);
                branchMatched = true;
                break;
            }
            catch (AssertionError | Exception exc) {
                try {
                    reader.reset();
                }
                catch (IOException exc1) {
                    // TODO Auto-generated catch block
                    exc1.printStackTrace();
                }
            }
        }
        if (!branchMatched) {
            throw new UnmarshalException("no branch matched on " + klass.getName());
        }
        return info;
    }

    /**
     * @param simpleType
     * @param reader
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private <T> T readSimpleValue(SimpleType simpleType, Class<T> klass, BitStreamReader reader)
        throws IOException {
        Object info = readValueViaAdapter(simpleType, Object.class, reader);
        if (info != null) {
            return (T) info;
        }
        int numBits = simpleType.getLength();
        if (simpleType.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        long value;
        if (simpleType.isUnsigned()) {
            value = reader.readBits(numBits);
        }
        else {
            value = reader.readSignedBits(numBits);
        }
        return convertLong(value, klass);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T convertLong(long value, Class<T> klass) {
        if (Integer.class.isAssignableFrom(klass)) {
            return (T) Integer.valueOf((int) value);
        }
        if (Short.class.isAssignableFrom(klass)) {
            return (T) Short.valueOf((short) value);
        }
        if (Byte.class.isAssignableFrom(klass)) {
            return (T) Byte.valueOf((byte) value);
        }
        return (T) (Long) value;       
    }

    private <T> T readValueViaAdapter(DadlType type, Class<T> klass, BitStreamReader reader)
        throws IOException {
        DadlAdapter<T> adapter = context.getAdapter(type, klass);
        if (adapter == null) {
            return null;
        }
        return adapter.unmarshal(reader);
    }
}
