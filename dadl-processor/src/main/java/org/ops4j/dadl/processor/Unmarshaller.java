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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.io.ByteArrayBitStreamReader;
import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.LengthField;
import org.ops4j.dadl.metamodel.gen.LengthKind;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.Tag;
import org.ops4j.dadl.model.ValidatedModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An unmarshaller deserializes info model objects from a bit stream using the formatting rules of a
 * given DADL model.
 *
 * @author hwellmann
 *
 */
public class Unmarshaller {

    private static Logger log = LoggerFactory.getLogger(Unmarshaller.class);

    private DadlContext context;
    private ValidatedModel model;
    private Evaluator evaluator;

    Unmarshaller(DadlContext context, ValidatedModel model) {
        this.context = context;
        this.model = model;
        this.evaluator = new Evaluator();
    }

    /**
     * Unmarshals the given byte array into an info model object of the given class. The class must
     * be mapped to a type in the current DADL model.
     *
     * @param bytes
     *            byte array
     * @param klass
     *            info model class
     * @return instance of model class
     * @throws IOException
     *             on read error
     */
    public <T> T unmarshal(byte[] bytes, Class<T> klass) throws IOException {
        String typeName = klass.getSimpleName();
        DadlType type = model.getType(typeName);
        try (BitStreamReader reader = new ByteArrayBitStreamReader(bytes)) {
            return unmarshal(type, klass, reader);
        }
    }

    private <T> T unmarshal(DadlType type, Class<T> klass, BitStreamReader reader)
        throws IOException {
        long startPos = reader.getBitPosition();
        T info = readValueViaAdapter(type, klass, reader);
        if (info == null) {
            info = newInstance(klass);
            evaluator.pushStack(info);
            try {
                if (type instanceof Sequence) {
                    info = unmarshalSequence(info, (Sequence) type, klass, reader);
                }
                else if (type instanceof Choice) {
                    info = unmarshalChoice(info, (Choice) type, klass, reader);
                }
                else {
                    throw new UnmarshalException("cannot unmarshal type " + klass.getName());
                }
            }
            finally {
                evaluator.popStack();
            }
        }
        skipPadding(type, startPos, reader);
        return info;
    }

    private void skipPadding(DadlType type, long startPos, BitStreamReader reader)
        throws IOException {
        if (type.getLengthKind() != LengthKind.EXPLICIT) {
            return;
        }
        int numBits = evaluator.computeLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        long actualNumBits = reader.getBitPosition() - startPos;
        if (actualNumBits == numBits) {
            return;
        }
        if (actualNumBits > numBits) {
            throw new UnmarshalException("actual length of " + type.getName()
                + " exceeds explicit length of " + numBits + " bits");
        }
        long paddingBits = numBits - actualNumBits;
        reader.skipBits(paddingBits);
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
        BitStreamReader reader) throws IOException {
        log.debug("unmarshalling sequence {}", sequence.getName());
        Tag tag = sequence.getTag();
        if (tag != null) {
            unmarshalTag(tag, reader);
        }
        LengthField lengthField = sequence.getLengthField();
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
            long actualTag = readSimpleValue(simpleType, null, Long.class, reader);
            long expectedTag = getExpectedValue(tag);
            log.debug("unmarshalling tag {}", expectedTag);
            if (actualTag != expectedTag) {
                String msg = String.format("tag mismatch: actual = %X, expected = %X", actualTag,
                    expectedTag);
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
            Long lengthValue = readSimpleValue(simpleType, null, Long.class, reader);
            log.debug("unmarshalled length field with value {}", lengthValue);
            return lengthValue;
        }
        throw new UnmarshalException("length field must have simple type");
    }

    private long getExpectedValue(Tag tag) {
        return Long.parseUnsignedLong(tag.getHexValue(), 16);
    }

    private void unmarshalSequenceField(Object info, Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        log.debug("unmarshalling sequence element {}", element.getName());
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
                evaluator.setProperty(element.getName(), fieldValue);
                // checkAssertion(info, field);
            }
        }
        catch (NoSuchFieldException | SecurityException exc) {
            // TODO Auto-generated catch block
            exc.printStackTrace();
        }
    }

    private void unmarshalSequenceListField(Object info, Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        switch (element.getOccursCountKind()) {
            case EXPRESSION:
                unmarshalSequenceListFieldByExpression(info, klass, element, reader);
                break;
            case PARSED:
                unmarshalSequenceListFieldParsed(info, klass, element, reader);
                break;
            default:
                throw new UnsupportedOperationException(element.getOccursCountKind().toString());

        }
    }

    private void unmarshalSequenceListFieldByExpression(Object info, Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        Long numItems = evaluator.evaluate(element.getOccursCount(), Long.class);

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) evaluator.getProperty(element.getName());

        for (long i = 0; i < numItems; i++) {
            Object fieldValue = unmarshalSequenceIndividualField(klass, element, reader);
            list.add(fieldValue);
        }
    }

    private void unmarshalSequenceListFieldParsed(Object info, Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) evaluator.getProperty(element.getName());

        while (true) {
            try {
                reader.mark();
                Object fieldValue = unmarshalSequenceIndividualField(klass, element, reader);
                list.add(fieldValue);
            }
            catch (AssertionError | Exception exc) {
                reader.reset();
                break;
            }
        }
    }

    private Object unmarshalSequenceIndividualField(Class<?> klass, Element element,
        BitStreamReader reader) throws IOException {
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof SimpleType) {
            return readSimpleValue((SimpleType) fieldType, element, klass, reader);
        }
        else {
            return unmarshal(fieldType, klass, reader);
        }
    }

    private <T> T unmarshalChoice(T info, Choice choice, Class<T> klass, BitStreamReader reader) {
        log.debug("unmarshalling choice {}", choice.getName());
        boolean branchMatched = false;
        for (Element element : choice.getElement()) {
            log.debug("trying branch {}", element.getName());
            reader.mark();
            try {
                String fieldName = element.getName();
                Field field = klass.getDeclaredField(fieldName);
                DadlType fieldType = model.getType(element.getType());

                Object fieldValue;
                if (fieldType instanceof SimpleType) {
                    fieldValue = readSimpleValue((SimpleType) fieldType, element, field.getType(),
                        reader);
                }
                else {
                    fieldValue = unmarshal(fieldType, field.getType(), reader);
                }
                evaluator.setProperty(fieldName, fieldValue);
                branchMatched = true;
                log.debug("matched branch {}", element.getName());
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

    @SuppressWarnings("unchecked")
    private <T> T readSimpleValue(SimpleType simpleType, Element element, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("reading simple value of type {}", simpleType.getName());
        Object info = readValueViaAdapter(simpleType, Object.class, reader);
        if (info != null) {
            return (T) info;
        }
        switch (simpleType.getContentType()) {
            case INTEGER:
                return readIntegerValue(simpleType, klass, reader);
            case TEXT:
                return (T) readTextValue(simpleType, element, reader);
            default:
                throw new UnsupportedOperationException(simpleType.getContentType().toString());
        }
    }

    private <T> T readIntegerValue(SimpleType simpleType, Class<T> klass, BitStreamReader reader)
        throws IOException {
        switch (simpleType.getRepresentation()) {
            case BINARY:
                return readIntegerValueAsBinary(simpleType, klass, reader);
            case TEXT:
                return readIntegerValueAsText(simpleType, klass, reader);
            default:
                throw new IllegalStateException();
        }
    }

    private <T> T readIntegerValueAsBinary(SimpleType simpleType, Class<T> klass,
        BitStreamReader reader) throws IOException {
        int numBits = evaluator.computeLength(simpleType);
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

    private <T> T readIntegerValueAsText(SimpleType simpleType, Class<T> klass,
        BitStreamReader reader) throws IOException {
        return null;
    }

    private String readTextValue(SimpleType type, DadlType representation, BitStreamReader reader)
        throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(representation);
            byte[] bytes = new byte[(int) length];
            reader.read(bytes);
            try {
                return new String(bytes, representation.getEncoding());
            }
            catch (UnsupportedEncodingException exc) {
                throw new UnmarshalException(exc);
            }
        }
        throw new UnsupportedOperationException();
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
