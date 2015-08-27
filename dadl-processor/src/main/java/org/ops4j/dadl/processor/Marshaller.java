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

import org.ops4j.dadl.exc.MarshalException;
import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.io.BitStreamWriter;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;
import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.LengthField;
import org.ops4j.dadl.metamodel.gen.LengthKind;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.Tag;
import org.ops4j.dadl.metamodel.gen.TaggedSequence;
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
    private SimpleTypeWriter simpleTypeWriter;

    Marshaller(DadlContext context, ValidatedModel model) {
        this.context = context;
        this.model = model;
        this.evaluator = new Evaluator();
        this.simpleTypeWriter = new SimpleTypeWriter(context, evaluator);
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
        if (!context.writeValueViaAdapter(type, info, writer)) {
            if (type instanceof Sequence) {
                marshalSequence(info, (Sequence) type, writer);
            }
            else if (type instanceof TaggedSequence) {
                marshalTaggedSequence(info, (TaggedSequence) type, writer);
            }
            else if (type instanceof Choice) {
                marshalChoice(info, (Choice) type, writer);
            }
            else {
                throw new MarshalException("cannot marshal type " + type.getClass().getName());
            }
        }
        fillPadding(type, startPos, writer);
    }

    private void fillPadding(DadlType type, long startPos, BitStreamWriter writer)
        throws IOException {
        boolean hasExactLength = (type.getLengthKind() == LengthKind.EXPLICIT);
        boolean hasMinLength = (type.getMinLength() != null);
        if (!(hasExactLength || hasMinLength)) {
            return;
        }

        long numBits = hasExactLength ?
            evaluator.computeLength(type) : evaluator.computeMinLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        long actualNumBits = writer.getBitPosition() - startPos;
        if (actualNumBits == numBits) {
            return;
        }
        if (actualNumBits > numBits) {
            if (hasMinLength) {
                return;
            }
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
            marshalSequencePayload(info, sequence, writer);
        }
        finally {
            evaluator.popStack();
        }
    }

    private void marshalTaggedSequence(Object info, TaggedSequence sequence, BitStreamWriter writer)
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
                marshalTaggedSequencePayload(info, sequence, writer);
            }
            else {
                ByteArrayBitStreamWriter payloadWriter = new ByteArrayBitStreamWriter();
                marshalTaggedSequencePayload(info, sequence, payloadWriter);
                long numPayloadBits = payloadWriter.getBitPosition();
                marshalLengthField(lengthField, numPayloadBits, writer);
                if (payloadWriter.getBitOffset() == 0) {
                    writer.write(payloadWriter.toByteArray());
                }
                else {
                    throw new UnsupportedOperationException(
                        "payload bitoffset != 0 is not supported");
                }
            }
        }
        finally {
            evaluator.popStack();
        }
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
            simpleTypeWriter.writeIntegerValueAsBinary(simpleType, numPayloadBits / 8, writer);
        }
        else {
            throw new UnmarshalException("length field must have simple type");
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

    private void marshalSequencePayload(Object info, Sequence sequence, BitStreamWriter writer)
        throws IOException {
        for (SequenceElement element : sequence.getElement()) {
            marshalSequenceField(info, element, writer);
        }
    }

    private void marshalTaggedSequencePayload(Object info, TaggedSequence sequence, BitStreamWriter writer)
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
            simpleTypeWriter.marshalEnumerationField(fieldInfo, element, (Enumeration) fieldType, writer);
        }
        else if (fieldType instanceof SimpleType) {
            simpleTypeWriter.marshalSimpleField(fieldInfo, element, (SimpleType) fieldType, writer);
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
            simpleTypeWriter.marshalSimpleField(fieldInfo, element, (SimpleType) fieldType, writer);
        }
        else {
            marshal(fieldInfo, fieldType, writer);
        }
    }

}
