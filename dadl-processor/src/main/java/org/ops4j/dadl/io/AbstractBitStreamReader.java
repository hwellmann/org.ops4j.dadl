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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.stream.ImageInputStreamImpl;

/**
 * Abstract base class for {@link BitStreamReader} implementations. Derived classes only need to
 * implement the {{@link #read()}, {@link #read(byte[], int, int)}, {{@link #seek(long)} and
 * {@link #length()} methods.
 *
 * @author hwellmann
 *
 */
public abstract class AbstractBitStreamReader extends ImageInputStreamImpl implements
    BitStreamReader {

    private ByteBuffer buffer = ByteBuffer.allocate(2048);

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
    public byte readByte() throws IOException {
        byte result;
        if (bitOffset == 0) {
            result = super.readByte();
        }
        else {
            result = (byte) readBits(BYTE_SIZE);
        }
        return result;
    }

    @Override
    public int readUnsignedByte() throws IOException {
        int result;
        if (bitOffset == 0) {
            result = super.readUnsignedByte();
        }
        else {
            result = (int) (readBits(BYTE_SIZE) & 0xFF);
        }
        return result;
    }

    @Override
    public short readShort() throws IOException {
        short result;
        if (bitOffset == 0) {
            result = super.readShort();
        }
        else {
            result = (short) readBits(SHORT_SIZE);
        }
        return result;
    }

    @Override
    public int readUnsignedShort() throws IOException {
        int result;
        if (bitOffset == 0) {
            result = super.readUnsignedShort();
        }
        else {
            result = (int) (readBits(SHORT_SIZE) & 0xFFFF);
        }
        return result;
    }

    @Override
    public int readInt() throws IOException {
        int result;
        if (bitOffset == 0) {
            result = super.readInt();
        }
        else {
            result = (int) readBits(INT_SIZE);
        }
        return result;
    }

    @Override
    public long readUnsignedInt() throws IOException {
        long result;
        if (bitOffset == 0) {
            result = super.readUnsignedInt();
        }
        else {
            result = readBits(INT_SIZE);
        }
        return result;
    }

    @Override
    public long readLong() throws IOException {
        long result;
        if (bitOffset == 0) {
            result = super.readLong();
        }
        else {
            result = readBits(LONG_SIZE);
        }
        return result;
    }

    @Override
    public BigInteger readBigInteger(int numBits) throws IOException {
        BigInteger result = BigInteger.ZERO;
        int toBeRead = numBits;
        if (toBeRead > BYTE_SIZE) {
            if (bitOffset != 0) {
                int prefixLength = BYTE_SIZE - bitOffset;
                long mostSignificantBits = readBits(prefixLength);
                result = BigInteger.valueOf(mostSignificantBits);
                toBeRead -= prefixLength;
            }

            int numBytes = toBeRead / BYTE_SIZE;
            byte[] b = new byte[numBytes];
            readFully(b);
            BigInteger i = new BigInteger(1, b);
            result = result.shiftLeft(BYTE_SIZE * numBytes);
            result = result.or(i);
            toBeRead %= BYTE_SIZE;
        }
        if (toBeRead > 0) {
            long value = readBits(toBeRead);
            result = result.shiftLeft(toBeRead);
            result = result.or(BigInteger.valueOf(value));
        }
        return result;
    }

    @Override
    public BigInteger readSignedBigInteger(int numBits) throws IOException {
        BigInteger result = readBigInteger(numBits);
        if (result.testBit(numBits - 1)) {
            result = result.subtract(BigInteger.ONE.shiftLeft(numBits));
        }
        return result;
    }

    @Override
    public String readString() throws IOException {
        buffer.rewind();
        while (true) {
            byte characterByte = this.readByte();
            if (characterByte == 0) {
                break;
            }
            buffer.put(characterByte);
        }
        byte[] bytes = Arrays.copyOf(buffer.array(), buffer.position());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void skipBits(long bitCnt) throws IOException {
        setBitPosition(getBitPosition() + bitCnt);
    }

    @Override
    public void alignTo(int alignVal) throws IOException {
        long bitPosition = getBitPosition();
        long newPosition = bitPosition;

        if (bitPosition % alignVal != 0) {
            newPosition = ((bitPosition / alignVal) + 1) * alignVal;
            setBitPosition(newPosition);
        }
    }

    @Override
    public long readSignedBits(int numBits) throws IOException {
        long result = readBits(numBits);
        if (result >= (1L << (numBits - 1))) {
            result -= 1L << numBits;
        }
        return result;
    }
}
