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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * @author hwellmann
 *
 */
public class BitStreamWriter extends MemoryCacheImageOutputStream {

    protected OutputStream os;

    public BitStreamWriter(OutputStream os) {
        super(os);
        this.os = os;
    }

    public long getBitPosition() {
        return 8 * streamPos + bitOffset;
    }

    public void setBitPosition(long pos) throws IOException {
        int newBitOffset = (int) (pos % 8);
        long newBytePos = pos / 8;
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
            writeBits(value, 8);
        }
    }

    @Override
    public void writeBytes(String value) throws IOException {
        if (bitOffset == 0) {
            super.writeBytes(value);
        }
        else {
            for (int i = 0; i < value.length(); i++) {
                writeBits(value.charAt(i), 8);
            }
        }
    }

    @Override
    public void writeShort(int value) throws IOException {
        if (bitOffset == 0) {
            super.writeShort(value);
        }
        else {
            writeBits(value, 16);
        }
    }

    @Override
    public void writeInt(int value) throws IOException {
        if (bitOffset == 0) {
            super.writeInt(value);
        }
        else {
            writeBits(value, 32);
        }
    }

    public void writeUnsignedInt(long value) throws IOException {
        writeBits(value, 32);
    }

    @Override
    public void writeLong(long value) throws IOException {
        if (bitOffset == 0) {
            super.writeLong(value);
        }
        else {
            writeBits(value, 64);
        }
    }

    public void byteAlign() throws IOException {
        if (bitOffset != 0) {
            writeBits(0, 8 - bitOffset);
        }
    }

    private void writeBitfield(BigInteger value, int numBits)
        throws IOException {
        if (numBits >= 64) {
            long val = value.longValue();
            writeBitfield(value.shiftRight(64), numBits - 64);
            writeLong(val);
        }
        else if (numBits >= 32) {
            int val = value.intValue();
            writeBitfield(value.shiftRight(32), numBits - 32);
            writeInt(val);
        }
        else if (numBits >= 16) {
            int val = value.shortValue();
            writeBitfield(value.shiftRight(16), numBits - 16);
            writeShort(val);
        }
        else if (numBits >= 8) {
            int val = value.byteValue();
            writeBitfield(value.shiftRight(8), numBits - 8);
            writeByte(val);
        }
        else {
            int val = value.byteValue();
            writeBits(val, numBits);
        }
    }

    public void writeBigInteger(BigInteger value, int numBits)
        throws IOException {
        writeBitfield(value, numBits);
    }

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

    public void skipBits(int bitCnt) throws IOException {
        setBitPosition(getBitPosition() + bitCnt);
    }

    public void alignTo(int alignVal) throws IOException {
        long bitPosition = getBitPosition();
        long newPosition = bitPosition;

        if (bitPosition % alignVal != 0) {
            newPosition = ((bitPosition / alignVal) + 1) * alignVal;
            long bytesToWrite = (newPosition - bitPosition) / 8;
            if (bytesToWrite > 0) {
                if ((newPosition - bitPosition) % 8 != 0)  {
                    bytesToWrite++;
                }
                byte[] b = new byte[(int) bytesToWrite];
                this.write(b, 0, b.length);
            }
            setBitPosition(newPosition);
        }
    }
}
