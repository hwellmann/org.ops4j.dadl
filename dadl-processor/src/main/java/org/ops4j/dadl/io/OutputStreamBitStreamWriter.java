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
package org.ops4j.dadl.io;

import static org.ops4j.dadl.io.Constants.BYTE_SIZE;
import static org.ops4j.dadl.io.Constants.INT_SIZE;
import static org.ops4j.dadl.io.Constants.LONG_SIZE;
import static org.ops4j.dadl.io.Constants.SHORT_SIZE;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Bit stream writer for an arbitrary output stream.
 *
 * @author hwellmann
 *
 */
public class OutputStreamBitStreamWriter extends MemoryCacheImageOutputStream implements
    BitStreamWriter {

    private OutputStream os;

    /**
     * Creates a bit stream writer wrapping the given output stream.
     *
     * @param os
     *            output stream.
     */
    public OutputStreamBitStreamWriter(OutputStream os) {
        super(os);
        this.os = os;
    }

    @Override
    public long getBitPosition() {
        return BYTE_SIZE * streamPos + bitOffset;
    }

    @Override
    public void setBitPosition(long pos) throws IOException {
        int newBitOffset = (int) (pos % BYTE_SIZE);
        long newBytePos = pos / BYTE_SIZE;
        seek(newBytePos);
        if (newBitOffset != 0) {
            setBitOffset(newBitOffset);
        }
    }

    @Override
    public void writeByte(int value) throws IOException {
        if (bitOffset == 0) {
            super.writeByte(value);
        }
        else {
            writeBits(value, BYTE_SIZE);
        }
    }

    @Override
    public void writeBytes(String value) throws IOException {
        if (bitOffset == 0) {
            super.writeBytes(value);
        }
        else {
            for (int i = 0; i < value.length(); i++) {
                writeBits(value.charAt(i), BYTE_SIZE);
            }
        }
    }

    @Override
    public void writeShort(int value) throws IOException {
        if (bitOffset == 0) {
            super.writeShort(value);
        }
        else {
            writeBits(value, SHORT_SIZE);
        }
    }

    @Override
    public void writeInt(int value) throws IOException {
        if (bitOffset == 0) {
            super.writeInt(value);
        }
        else {
            writeBits(value, INT_SIZE);
        }
    }

    @Override
    public void writeUnsignedInt(long value) throws IOException {
        writeBits(value, INT_SIZE);
    }

    @Override
    public void writeLong(long value) throws IOException {
        if (bitOffset == 0) {
            super.writeLong(value);
        }
        else {
            writeBits(value, LONG_SIZE);
        }
    }

    @Override
    public void byteAlign() throws IOException {
        if (bitOffset != 0) {
            writeBits(0, BYTE_SIZE - bitOffset);
        }
    }

    private void writeBitfield(BigInteger value, int numBits) throws IOException {
        if (numBits >= LONG_SIZE) {
            long val = value.longValue();
            writeBitfield(value.shiftRight(LONG_SIZE), numBits - LONG_SIZE);
            writeLong(val);
        }
        else if (numBits >= INT_SIZE) {
            int val = value.intValue();
            writeBitfield(value.shiftRight(INT_SIZE), numBits - INT_SIZE);
            writeInt(val);
        }
        else if (numBits >= SHORT_SIZE) {
            int val = value.shortValue();
            writeBitfield(value.shiftRight(SHORT_SIZE), numBits - SHORT_SIZE);
            writeShort(val);
        }
        else if (numBits >= BYTE_SIZE) {
            int val = value.byteValue();
            writeBitfield(value.shiftRight(BYTE_SIZE), numBits - BYTE_SIZE);
            writeByte(val);
        }
        else {
            int val = value.byteValue();
            writeBits(val, numBits);
        }
    }

    @Override
    public void writeBigInteger(BigInteger value, int numBits) throws IOException {
        writeBitfield(value, numBits);
    }

    @Override
    public void writeZeroTerminatedString(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bitOffset == 0) {
            write(bytes);
        }
        else {
            for (byte b : bytes) {
                writeByte(b);
            }
        }
        writeByte(0);
    }

    @Override
    public void skipBits(int bitCnt) throws IOException {
        setBitPosition(getBitPosition() + bitCnt);
    }

    @Override
    public void alignTo(int alignVal) throws IOException {
        long bitPosition = getBitPosition();
        long newPosition = bitPosition;

        if (bitPosition % alignVal != 0) {
            newPosition = ((bitPosition / alignVal) + 1) * alignVal;
            long bytesToWrite = (newPosition - bitPosition) / 8;
            if (bytesToWrite > 0) {
                if ((newPosition - bitPosition) % 8 != 0) {
                    bytesToWrite++;
                }
                byte[] b = new byte[(int) bytesToWrite];
                this.write(b, 0, b.length);
            }
            setBitPosition(newPosition);
        }
    }

    protected OutputStream getStream() {
        return os;
    }
}
