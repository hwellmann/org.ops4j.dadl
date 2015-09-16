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

import static org.ops4j.dadl.io.Constants.BYTE_SIZE;
import static org.ops4j.dadl.io.Constants.DEC_BASE;
import static org.ops4j.dadl.io.Constants.NIBBLE_SIZE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.LengthKind;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads values of simple types from a bit stream using the formatting rules of a given DADL model.
 *
 * @author hwellmann
 *
 */
public class SimpleTypeReader {

    private static Logger log = LoggerFactory.getLogger(SimpleTypeReader.class);

    private DadlContext context;
    private Evaluator evaluator;

    SimpleTypeReader(DadlContext context, Evaluator evaluator) {
        this.context = context;
        this.evaluator = evaluator;
    }

    @SuppressWarnings("unchecked")
    <T> T readEnumerationValue(Enumeration enumeration, Element element, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("reading simple value of type {}", enumeration.getName());
        Object info = context.readValueViaAdapter(enumeration, reader);
        if (info != null) {
            return (T) info;
        }
        switch (enumeration.getContentType()) {
            case INTEGER:
                info = readIntegerValue(enumeration, element, klass, reader);
                break;
            case TEXT:
                info = readTextValue(enumeration, element, reader);
                break;
            default:
                throw new UnsupportedOperationException(enumeration.getContentType().toString());
        }
        info = evaluator.setSelfEnumeration(info, klass);
        evaluator.checkDiscriminator(element);
        return (T) info;
    }

    @SuppressWarnings("unchecked")
    <T> T readSimpleValue(SimpleType simpleType, Element element, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("reading simple value of type {}", simpleType.getName());
        Object info = context.readValueViaAdapter(simpleType, reader);
        if (info != null) {
            return (T) info;
        }
        switch (simpleType.getContentType()) {
            case INTEGER:
                info = readIntegerValue(simpleType, element, klass, reader);
                log.debug("value: {}", info);
                break;
            case TEXT:
                info = readTextValue(simpleType, element, reader);
                log.debug("value: {}", info);
                break;
            case OPAQUE:
                info = readOpaqueValue(simpleType, element, reader);
                break;
            default:
                throw new UnsupportedOperationException(simpleType.getContentType().toString());
        }
        evaluator.setSelf(info);
        evaluator.checkDiscriminator(element);
        return (T) info;
    }

    Number readIntegerValue(SimpleType simpleType, Element element, Class<?> klass,
        BitStreamReader reader) throws IOException {
        DadlType type = (element == null) ? simpleType : element;
        switch (simpleType.getRepresentation()) {
            case BINARY:
                return readIntegerValueAsBinary(type, klass, reader);
            case TEXT:
                return readIntegerValueAsText(type, klass, reader);
            default:
                throw new IllegalStateException();
        }
    }

    Number readIntegerValueAsBinary(DadlType simpleType, Class<?> klass,
        BitStreamReader reader) throws IOException {
        switch (context.getBinaryNumberRep(simpleType)) {
            case BINARY:
                return readIntegerValueAsStandardBinary(simpleType, klass, reader);
            case BCD:
                return readIntegerValueAsBcdBinary(simpleType, klass, reader);
            default:
                throw new UnsupportedOperationException("unsupported binaryNumberRep = "
                    + simpleType.getBinaryNumberRep());
        }
    }

    Number readIntegerValueAsStandardBinary(DadlType simpleType, Class<?> klass,
        BitStreamReader reader) throws IOException {
        int numBits = evaluator.computeLength(simpleType);
        if (simpleType.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= BYTE_SIZE;
        }
        long value;
        if (Boolean.TRUE.equals(simpleType.isUnsigned())) {
            value = reader.readBits(numBits);
        }
        else {
            value = reader.readSignedBits(numBits);
        }
        return convertLong(value, klass);
    }

    Number readIntegerValueAsBcdBinary(DadlType simpleType, Class<?> klass,
        BitStreamReader reader) throws IOException {
        int numBits = evaluator.computeLength(simpleType);
        if (simpleType.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= BYTE_SIZE;
        }
        if (numBits % NIBBLE_SIZE != 0) {
            throw new UnmarshalException("BCD bit length must be divisible by 4");
        }
        int numDigits = numBits / NIBBLE_SIZE;
        long value = 0;
        for (int i = 0; i < numDigits; i++) {
            value *= DEC_BASE;
            long digit = reader.readBits(NIBBLE_SIZE);
            // TODO signed numbers, assume non-negative for now
            if (digit >= DEC_BASE) {
                throw new UnmarshalException("illegal digit: " + digit);
            }
            value += digit;
        }

        return convertLong(value, klass);
    }

    Number readIntegerValueAsText(DadlType type, Class<?> klass,
        BitStreamReader reader) throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(type);
            byte[] bytes = readBytes(reader, length);
            String s = new String(bytes, StandardCharsets.UTF_8);
            return convertLong(Long.parseLong(s), klass);
        }
        throw new UnsupportedOperationException();
    }

    String readTextValue(SimpleType type, DadlType representation, BitStreamReader reader)
        throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(representation);
            byte[] bytes = readBytes(reader, length);
            try {
                return new String(bytes, representation.getEncoding());
            }
            catch (UnsupportedEncodingException exc) {
                throw new UnmarshalException(exc);
            }
        }
        throw new UnsupportedOperationException();
    }

    /**
     * @param reader
     * @param length
     * @return
     * @throws IOException
     */
    private byte[] readBytes(BitStreamReader reader, long length) throws IOException {
        byte[] bytes = new byte[(int) length];
        int numBytes = reader.read(bytes);
        if (numBytes < length) {
            String msg = String.format("expected %d bytes, read %d bytes", length, numBytes);
            throw new UnmarshalException(msg);
        }
        return bytes;
    }

    byte[] readOpaqueValue(SimpleType type, DadlType representation, BitStreamReader reader)
        throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(representation);
            return readBytes(reader, length);
        }
        throw new UnsupportedOperationException();
    }

    Number convertLong(long value, Class<?> klass) {
        if (Integer.class.isAssignableFrom(klass)) {
            return Integer.valueOf((int) value);
        }
        if (Short.class.isAssignableFrom(klass)) {
            return Short.valueOf((short) value);
        }
        if (Byte.class.isAssignableFrom(klass)) {
            return Byte.valueOf((byte) value);
        }
        return value;
    }
}
