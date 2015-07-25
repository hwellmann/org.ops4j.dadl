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
import java.util.ArrayList;
import java.util.List;

import javax.el.ELProcessor;

import org.ops4j.dadl.io.BitStreamWriter;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;
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

/**
 * @author hwellmann
 *
 */
public class Marshaller {

    private DadlContext context;
    private ValidatedModel model;
    private List<Object> infoStack;
    private ELProcessor processor;

    /**
     *
     */
    public Marshaller(DadlContext context, ValidatedModel model) {
        this.context = context;
        this.model = model;
        this.infoStack = new ArrayList<>();
        this.processor = new ELProcessor();
        processor.setValue("up", infoStack);
    }

    public void marshal(Object info, OutputStream os) throws IOException {
        String typeName = info.getClass().getSimpleName();
        DadlType type = model.getType(typeName);
        try (BitStreamWriter writer = new BitStreamWriter(os)) {
            marshal(info, type, writer);
        }
    }

    private void marshal(Object info, DadlType type, BitStreamWriter writer) throws IOException {
        long startPos = writer.getBitPosition();
        if (!writeValueViaAdapter(type, info, writer)) {
            pushStack(info);
            try {
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
            finally {
                popStack();
            }
        }
        fillPadding(type, startPos, writer);
    }

    private void fillPadding(DadlType type, long startPos, BitStreamWriter writer)
        throws IOException {
        if (type.getLengthKind() != LengthKind.EXPLICIT) {
            return;
        }
        long numBits = computeLength(type, processor);
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
            throw new UnmarshalException("number of padding bits must by divisible by 8");
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

    private long computeLength(DadlType type, ELProcessor elProcessor) {
        return (Long) elProcessor.getValue(type.getLength(), Long.class);
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

    /**
     * @param info
     * @param klass
     * @param sequence
     * @param writer
     * @throws IOException
     */
    private void marshalSequence(Object info, Sequence sequence, BitStreamWriter writer)
        throws IOException {
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

    private void marshalChoice(Object info, Choice choice, BitStreamWriter writer)
        throws IOException {
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
            long numBits = computeLength(simpleType, processor);
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
            writeSimpleValue(simpleType, numPayloadBits / 8, writer);
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
            Object fieldInfo = processor.eval("self." + element.getName());
            marshalSequenceIndividualField(fieldInfo, element, writer);
        }
    }

    private void marshalSequenceIndividualField(Object fieldInfo, SequenceElement element,
        BitStreamWriter writer) throws IOException {
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof SimpleType) {
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
        List<Object> items = (List<Object>) processor.eval("self." + element.getName());
        for (Object item : items) {
            marshalSequenceIndividualField(item, element, writer);
        }
    }

    private void marshalChoiceField(Object fieldInfo, Element element, BitStreamWriter writer)
        throws IOException {
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof SimpleType) {
            marshalSimpleField(fieldInfo, element, (SimpleType) fieldType, writer);
        }
        else {
            marshal(fieldInfo, fieldType, writer);
        }
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
        Object calculatedValue = calculateValue(fieldInfo, element, type);
        switch (type.getContentType()) {
            case INTEGER:
                marshalIntegerField(calculatedValue, element, type, writer);
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
                return processor.eval(expr);
            }
        }
        return fieldInfo;
    }

    private void marshalIntegerField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        switch (type.getRepresentation()) {
            case BINARY:
                writeSimpleValue(type, fieldInfo, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported representation: "
                    + type.getRepresentation());
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

    private void writeSimpleValue(SimpleType type, Object info, BitStreamWriter writer)
        throws IOException {
        if (writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
        }

        long numBits = computeLength(type, processor);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        writer.writeBits(value, (int) numBits);
    }
}
