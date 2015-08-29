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

import javax.imageio.stream.ImageOutputStream;

/**
 * Writes integer and string values to a bit stream.
 *
 * @author hwellmann
 *
 */
public interface BitStreamWriter extends ImageOutputStream {

    /**
     * Gets the current bit position in the stream. This is the position of the bit that will be
     * written next. The first bit has position 0.
     *
     * @return bit position
     */
    long getBitPosition();

    /**
     * Sets the current bit position in the stream. This can be used to skip or re-write parts of
     * the stream. This method does not write any bits to the stream.
     *
     * @param pos
     *            new bit position
     */
    void setBitPosition(long pos) throws IOException;

    /**
     * Writes an unsigned integer to the stream.
     *
     * @param value
     *            unsigned integer value (less than 2^32).
     * @throws IOException
     */
    void writeUnsignedInt(long value) throws IOException;

    /**
     * Aligns to the next byte boundary, filling any skipped bits with 0. Shorthand for
     * {@code alignTo(8)}.
     *
     * @throws IOException
     */
    void byteAlign() throws IOException;

    /**
     * Writes a signed bit integer value to the stream.
     *
     * @param value
     *            value to be written
     * @param numBits
     *            number of bits to be written (including sign)
     * @throws IOException
     */
    void writeBigInteger(BigInteger value, int numBits) throws IOException;

    /**
     * Writes a zero-terminated UTF-8 string.
     *
     * @param value
     *            string value (not including the zero terminator)
     * @throws IOException
     */
    void writeZeroTerminatedString(String value) throws IOException;

    /**
     * Skips the given number of bits, not writing anything to the stream.
     *
     * @param numBits
     *            number of bits to be skipped
     * @throws IOException
     */
    void skipBits(int numBits) throws IOException;

    /**
     * Skips forward to the next bit position which is divisible by the given alignment value. The
     * bit position remains unchanged when the current position is aligned. Any skipped bits are
     * filled with 0.
     *
     * @param alignment
     *            divisor of bit position
     * @throws IOException
     */
    void alignTo(int alignment) throws IOException;
}
