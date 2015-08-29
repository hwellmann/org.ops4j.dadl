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

import javax.imageio.stream.ImageInputStream;

/**
 * Reads integer and string values from a bit stream.
 *
 * @author hwellmann
 *
 */
public interface BitStreamReader extends ImageInputStream {

    /**
     * Gets the current bit position in the stream. This is the position of the bit that will be
     * read next. The first bit has position 0.
     *
     * @return bit position
     */
    long getBitPosition();

    /**
     * Sets the current bit position in the stream. This can be used to skip or re-read parts of the
     * stream.
     *
     * @param pos
     *            new bit position
     */
    void setBitPosition(long pos) throws IOException;

    /**
     * Reads an unsigned {@code BigInteger} with the given number of bits.
     *
     * @param numBits
     *            number of bits
     * @return big integer
     * @throws IOException
     */
    BigInteger readBigInteger(int numBits) throws IOException;

    /**
     * Reads an signed {@code BigInteger} in two's complement with the given number of bits. The
     * first bit is the sign
     *
     * @param numBits
     *            number of bits
     * @return big integer
     * @throws IOException
     */
    BigInteger readSignedBigInteger(int numBits) throws IOException;

    /**
     * Reads a zero-terminated string in UTF-8 encoding.
     *
     * @return string, not including the terminating zero byte
     * @throws IOException
     */
    String readString() throws IOException;

    /**
     * Skips the given number of bits.
     *
     * @param numBits
     *            number of bits to be skipped
     * @throws IOException
     */
    void skipBits(long numBits) throws IOException;

    /**
     * Skips forward to the next bit position which is divisible by the given alignment value. The
     * bit position remains unchanged when the current position is aligned.
     *
     * @param alignment
     * @throws IOException
     */
    void alignTo(int alignment) throws IOException;

    /**
     * Reads the given number of bits, interpreted as a signed binary number in two's complement.
     *
     * @param numBits
     *            number of bits to be read (64 or less)
     * @return signed value
     * @throws IOException
     */
    long readSignedBits(int numBits) throws IOException;
}
