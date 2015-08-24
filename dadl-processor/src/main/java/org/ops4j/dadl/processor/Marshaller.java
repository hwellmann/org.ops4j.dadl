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
import java.io.OutputStream;
import java.util.List;

import javax.el.ELProcessor;

import org.ops4j.dadl.io.BitStreamWriter;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;
import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.Justification;
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
 * A marshaller serializes info model objects to a bit stream using the formatting rules of a given
 * DADL model.
 *
 * @author hwellmann
 *
 */
public class Marshaller {

    private static Logger log = LoggerFactory.getLogger(Marshaller.class);

    private DadlContext context;
    private ValidatedModel model;
    private Evaluator evaluator;

    Marshaller(DadlContext context, ValidatedModel model) {
        this.context = context;
        this.model = model;
        this.evaluator = new Evaluator();
    }

    /**
     * Marshals (serializes) the given info model object to the given bit stream.
     *
     * @param info
     *            info model object
     * @param os
     *            output stream
     * @throws IOException
     *             on write error
     */
    public void marshal(Object info, OutputStream os) throws IOException {
        String typeName = info.getClass().getSimpleName();
        DadlType type = model.getType(typeName);
        try (BitStreamWriter writer = new BitStreamWriter(os)) {
            marshal(info, type, writer);
        }
    }

    private void marshal(Object info, DadlType type, BitStreamWriter writer) throws IOException {
        evaluator.setSelf(info);
        long startPos = writer.getBitPosition();
        if (!writeValueViaAdapter(type, info, writer)) {
            if (type instanceof Sequence) {
                marshalSequence(info, (Sequence) type, writer);
            }
            else if (type instanceof Choice) {
                marshalChoice(info, (Choice) type, writer);
            }
            else {
                throw new UnmarshalException("cannot marshal type " + type.getClass().getName());
            }
        }
        fillPadding(type, startPos, writer);
    }

    private void fillPadding(DadlType type, long startPos, BitStreamWriter writer)
        throws IOException {
        if (type.getLengthKind() != LengthKind.EXPLICIT) {
            return;
        }
        long numBits = evaluator.computeLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        long actualNumBits = writer.getBitPosition() - startPos;
        if (actualNumBits == numBits) {
            return;
        }
        if (actualNumBits > numBits) {
            throw new UnmarshalException("actual length of " + type.getName()
                + " exceeds explicit length of " + numBits + " bits");
        }
        long paddingBits = numBits - actualNumBits;
        if (paddingBits % 8 != 0) {
            throw new UnmarshalException("number of padding bits must be divisible by 8");
        }
        long paddingBytes = paddingBits / 8;
        int fillByte = 0;
        if (type.getFillByte() != null) {
            fillByte = type.getFillByte();
        }
        for (int i = 0; i < paddingBytes; i++) {
            writer.write(fillByte);
        }
    }

    /**
     * @param info
     * @param klass
     * @param sequence
     * @param writer
     * @throws IOException
     */
    private void marshalSequence(Object info, Sequence sequence, BitStreamWriter writer)
        throws IOException {
        log.debug("marshalling sequence {}", sequence.getName());
        evaluator.pushStack();
        try {
        Tag tag = sequence.getTag();
        if (tag != null) {
            marshalTag(tag, writer);
        }
        LengthField lengthField = sequence.getLengthField();
        if (lengthField == null) {
            marshalSequencePayload(info, sequence, writer);
        }
        else {
            ByteArrayBitStreamWriter payloadWriter = new ByteArrayBitStreamWriter();
            marshalSequencePayload(info, sequence, payloadWriter);
            long numPayloadBits = payloadWriter.getBitPosition();
            marshalLengthField(lengthField, numPayloadBits, writer);
            if (payloadWriter.getBitOffset() == 0) {
                writer.write(payloadWriter.toByteArray());
            }
            else {
                throw new UnsupportedOperationException("payload bitoffset != 0 is not supported");
            }
        }
        }
        finally {
            evaluator.popStack();
        }
    }

    private void marshalChoice(Object info, Choice choice, BitStreamWriter writer)
        throws IOException {
        log.debug("marshalling choice {}", choice.getType());
        evaluator.pushStack();
        try {
            ELProcessor processor = new ELProcessor();
            processor.defineBean("self", info);
            boolean branchMatched = false;
            for (Element element : choice.getElement()) {
                Object fieldInfo = processor.eval("self." + element.getName());
                if (fieldInfo != null) {
                    marshalChoiceField(fieldInfo, element, writer);
                    branchMatched = true;
                    break;
                }
            }
            if (!branchMatched) {
                throw new MarshalException("all branches empty in choice: " + info);
            }
        }
        finally {
            evaluator.popStack();
        }
    }

    /**
     * @param tag
     * @param writer
     * @throws IOException
     */
    private void marshalTag(Tag tag, BitStreamWriter writer) throws IOException {
        String typeName = tag.getType();
        Object type = model.getType(typeName);
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            long numBits = evaluator.computeLength(simpleType);
            if (simpleType.getLengthUnit() == LengthUnit.BYTE) {
                numBits *= 8;
            }
            long tagValue = getExpectedValue(tag);
            writer.writeBits(tagValue, (int) numBits);
        }
        else {
            throw new UnmarshalException("tag type is not a simple type: " + typeName);
        }
    }

    private long getExpectedValue(Tag tag) {
        return Long.parseUnsignedLong(tag.getHexValue(), 16);
    }

    /**
     * @param lengthField
     * @param numPayloadBits
     * @param writer
     * @throws IOException
     */
    private void marshalLengthField(LengthField lengthField, long numPayloadBits,
        BitStreamWriter writer) throws IOException {
        DadlType type = model.getType(lengthField.getType());
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            writeIntegerValueAsBinary(simpleType, numPayloadBits / 8, writer);
        }
        else {
            throw new UnmarshalException("length field must have simple type");
        }
    }

    private void marshalSequencePayload(Object info, Sequence sequence, BitStreamWriter writer)
        throws IOException {
        for (SequenceElement element : sequence.getElement()) {
            marshalSequenceField(info, element, writer);
        }
    }

    private void marshalSequenceField(Object info, SequenceElement element, BitStreamWriter writer)
        throws IOException {
        if (model.isList(element)) {
            marshalSequenceListField(info, element, writer);
        }
        else {
            Object fieldInfo = evaluator.getParentProperty(element.getName());
            marshalSequenceIndividualField(fieldInfo, element, writer);
        }
    }

    private void marshalSequenceIndividualField(Object fieldInfo, SequenceElement element,
        BitStreamWriter writer) throws IOException {
        log.debug("marshalling field {}", element.getName());
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof Enumeration) {
            marshalEnumerationField(fieldInfo, element, (Enumeration) fieldType, writer);
        }
        else if (fieldType instanceof SimpleType) {
            marshalSimpleField(fieldInfo, element, (SimpleType) fieldType, writer);
        }
        else {
            marshal(fieldInfo, fieldType, writer);
        }
    }

    /**
     * @param info
     * @param element
     * @param writer
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private void marshalSequenceListField(Object info, SequenceElement element,
        BitStreamWriter writer) throws IOException {
        log.debug("marshalling list field {}", element.getName());
        List<Object> items = (List<Object>) evaluator.getParentProperty(element.getName());
        int index = 0;
        for (Object item : items) {
            log.debug("index {}", index);
            marshalSequenceIndividualField(item, element, writer);
            index++;
        }
    }

    private void marshalChoiceField(Object fieldInfo, Element element, BitStreamWriter writer)
        throws IOException {
        log.debug("marshalling branch {}", element.getName());
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof SimpleType) {
            marshalSimpleField(fieldInfo, element, (SimpleType) fieldType, writer);
        }
        else {
            marshal(fieldInfo, fieldType, writer);
        }
    }

    private void marshalEnumerationField(Object fieldInfo, Element element, Enumeration enumeration,
        BitStreamWriter writer) throws IOException {
        Object rawValue = evaluator.getEnumerationValue(fieldInfo);
        marshalSimpleField(rawValue, element, enumeration, writer);
    }

    /**
     * @param info
     * @param klass
     * @param field
     * @param writer
     * @throws IOException
     */
    private void marshalSimpleField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        log.debug("writing simple value of type {}", type.getName());
        Object calculatedValue = calculateValue(fieldInfo, element, type);
        switch (type.getContentType()) {
            case INTEGER:
                marshalIntegerField(calculatedValue, element, type, writer);
                break;
            case TEXT:
                marshalTextField(calculatedValue, element, type, writer);
                break;
            case OPAQUE:
                marshalOpaqueField(calculatedValue, element, type, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported content type: "
                    + type.getContentType());
        }
    }

    /**
     * @param fieldInfo
     * @param element
     * @param type
     * @return
     */
    private Object calculateValue(Object fieldInfo, Element element, SimpleType type) {
        if (element instanceof SequenceElement) {
            SequenceElement seqElem = (SequenceElement) element;
            String expr = seqElem.getOutputValueCalc();
            if (expr != null) {
                Object value = evaluator.evaluate(expr);
                evaluator.setParentProperty(element.getName(), value);
                return value;
            }
        }
        return fieldInfo;
    }

    private void marshalIntegerField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        switch (type.getRepresentation()) {
            case BINARY:
                writeIntegerValueAsBinary(type, fieldInfo, writer);
                break;
            case TEXT:
                writeIntegerValueAsText(element, fieldInfo, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported representation: "
                    + type.getRepresentation());
        }
    }

    private void marshalTextField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        if (fieldInfo instanceof String) {
            String text = (String) fieldInfo;
            long length = evaluator.computeLength(element);
            if (length != text.length()) {
                throw new UnmarshalException("computed text length does not match actual length");
            }
            byte[] bytes = text.getBytes(element.getEncoding());
            writer.write(bytes, 0, bytes.length);
        }
    }

    private void marshalOpaqueField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        if (fieldInfo instanceof byte[]) {
            byte[] bytes = (byte[]) fieldInfo;
            long length = evaluator.computeLength(element);
            if (length != bytes.length) {
                throw new UnmarshalException("computed length does not match actual length");
            }
            writer.write(bytes, 0, bytes.length);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean writeValueViaAdapter(DadlType type, Object info, BitStreamWriter writer)
        throws IOException {
        DadlAdapter adapter = context.getAdapter(type, info.getClass());
        if (adapter == null) {
            return false;
        }
        else {
            adapter.marshal(info, writer);
            return true;
        }
    }

    private void writeIntegerValueAsBinary(SimpleType type, Object info, BitStreamWriter writer)
        throws IOException {
        switch (type.getBinaryNumberRep()) {
            case BINARY:
                writeIntegerValueAsStandardBinary(type, info, writer);
                break;
            case BCD:
                writeIntegerValueAsBcdBinary(type, info, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported binaryNumberRep = "
                    + type.getBinaryNumberRep());
        }
    }

    private void writeIntegerValueAsStandardBinary(SimpleType type, Object info,
        BitStreamWriter writer) throws IOException {

        evaluator.setSelf(info);
        if (writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
        }

        long numBits = evaluator.computeLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        writer.writeBits(value, (int) numBits);
    }

    private void writeIntegerValueAsBcdBinary(SimpleType type, Object info, BitStreamWriter writer)
        throws IOException {
        evaluator.setSelf(info);
        if (writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
        }

        long numBits = evaluator.computeLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        if (numBits % 4 != 0) {
            throw new UnmarshalException("BCD bit length must be divisible by 4");
        }
        long numDigits = numBits / 4;
        String s = Long.toString(value);
        long numPaddingDigits = numDigits - s.length();
        if (numPaddingDigits < 0) {
            throw new UnmarshalException("value too large for " + numDigits + " digits");
        }
        for (int i = 0; i < numPaddingDigits; i++) {
            writer.writeBits(0, 4);
        }
        for (int i = 0; i < s.length(); i++) {
            writer.writeBits(s.charAt(i) - '0', 4);
        }
    }

    private void writeIntegerValueAsText(DadlType type, Object info, BitStreamWriter writer)
        throws IOException {
        evaluator.setSelf(info);
        if (writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
            String s = Long.toString(value);
            int numBytes = evaluator.computeLength(type);
            if (s.length() > numBytes) {
                throw new MarshalException(numBytes + " bytes are not sufficient for value " + s);
            }
            writeTextWithPadding(s, numBytes, type.getTextNumberJustification(),
                type.getTextNumberPadCharacter(), writer);
        }
    }

    private void writeTextWithPadding(String s, int numBytes, Justification justification,
        String padCharacter, BitStreamWriter writer) throws IOException {
        int totalPadding = numBytes - s.length();
        int leftPadding = totalPadding;
        int rightPadding = totalPadding;
        switch (justification) {
            case LEFT:
                leftPadding = 0;
                break;
            case RIGHT:
                rightPadding = 0;
                break;
            case CENTER:
                leftPadding /= 2;
                rightPadding = leftPadding;
                if (leftPadding + rightPadding < totalPadding) {
                    leftPadding++;
                }
                break;
        }
        for (int i = 0; i < leftPadding; i++) {
            writer.writeBytes(padCharacter);
        }
        writer.writeBytes(s);
        for (int i = 0; i < rightPadding; i++) {
            writer.writeBytes(padCharacter);
        }
    }
}
