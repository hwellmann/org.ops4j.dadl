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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.imageio.stream.ImageInputStreamImpl;

/**
 * @author hwellmann
 *
 */
public abstract class BitStreamReader extends ImageInputStreamImpl {

    private ByteBuffer buffer = ByteBuffer.allocate(2048);
    private Charset charset = Charset.forName("UTF-8");

    public long getBitPosition() {
        long pos = 8 * streamPos + bitOffset;
        return pos;
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
    public byte readByte() throws IOException {
        byte result;
        if (bitOffset == 0) {
            result = super.readByte();
        }
        else {
            result = (byte) readBits(8);
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
            result = (int) (readBits(8) & 0xFF);
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
            result = (short) readBits(16);
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
            result = (int) (readBits(16) & 0xFFFF);
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
            result = (int) readBits(32);
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
            result = readBits(32);
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
            result = readBits(64);
        }
        return result;
    }

    public BigInteger readBigInteger(int numBits) throws IOException {
        BigInteger result = BigInteger.ZERO;
        int toBeRead = numBits;
        if (toBeRead > 8) {
            if (bitOffset != 0) {
                int prefixLength = 8 - bitOffset;
                long mostSignificantBits = readBits(prefixLength);
                result = BigInteger.valueOf(mostSignificantBits);
                toBeRead -= prefixLength;
            }

            int numBytes = toBeRead / 8;
            byte[] b = new byte[numBytes];
            readFully(b);
            BigInteger i = new BigInteger(1, b);
            result = result.shiftLeft(8 * numBytes);
            result = result.or(i);
            toBeRead %= 8;
        }
        if (toBeRead > 0) {
            long value = readBits(toBeRead);
            result = result.shiftLeft(toBeRead);
            result = result.or(BigInteger.valueOf(value));
        }
        return result;
    }

    public BigInteger readSignedBigInteger(int numBits) throws IOException {
        BigInteger result = readBigInteger(numBits);
        if (result.testBit(numBits - 1)) {
            result.subtract(BigInteger.ONE.shiftLeft(numBits));
        }
        return result;
    }

    public String readString() throws IOException {
        buffer.rewind();
        while (true) {
            byte characterByte = this.readByte();
            if (characterByte == 0)
                break;
            buffer.put(characterByte);
        }
        byte[] bytes = Arrays.copyOf(buffer.array(), buffer.position());
        String result = new String(bytes, charset);
        return result;
    }

    public void skipBits(int bitCnt) throws IOException {
        setBitPosition(getBitPosition() + bitCnt);
    }

    public void alignTo(int alignVal) throws IOException {
        long bitPosition = getBitPosition();
        long newPosition = bitPosition;

        if (bitPosition % alignVal != 0) {
            newPosition = ((bitPosition / alignVal) + 1) * alignVal;
            setBitPosition(newPosition);
        }
    }

    public long readSignedBits(int numBits) throws IOException {
        long result = readBits(numBits);
        if (result >= (1L << (numBits - 1))) {
            result -= 1L << numBits;
        }
        return result;
    }
}
