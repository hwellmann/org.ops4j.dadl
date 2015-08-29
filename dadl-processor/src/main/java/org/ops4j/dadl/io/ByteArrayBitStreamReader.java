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

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * A {@link BitStreamReader} backed by a byte array in memory.
 *
 * @author hwellmann
 *
 */
public class ByteArrayBitStreamReader extends AbstractBitStreamReader {

    private ByteArrayInputStream bais;
    private long numBytes;

    /**
     * Constructs a bit stream reader reading from a segment of the given byte array.
     *
     * @param b
     *            byte array
     * @param offset
     *            offset of segment, relative to position 0
     * @param length
     *            length of the segment
     */
    public ByteArrayBitStreamReader(byte[] b, int offset, int length) {
        bais = new ByteArrayInputStream(b, offset, length);
        this.numBytes = length;
    }

    /**
     * Constructs a bit stream reader reading from the given byte array.
     *
     * @param b
     *            byte array
     */
    public ByteArrayBitStreamReader(byte[] b) {
        bais = new ByteArrayInputStream(b);
        numBytes = b.length;
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        int val = bais.read();
        if (val != -1) {
            ++streamPos;
        }
        return val;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkClosed();
        bitOffset = 0;
        int nbytes = bais.read(b, off, len);
        if (nbytes != -1) {
            streamPos += nbytes;
        }
        return nbytes;
    }

    @Override
    public long length() {
        return numBytes;
    }

    @Override
    public void seek(long pos) throws IOException {
        checkClosed();
        if (pos < flushedPos) {
            throw new IndexOutOfBoundsException("pos < flushedPos!");
        }
        bitOffset = 0;
        bais.reset();
        long numSkippedBytes = bais.skip(pos);
        if (numSkippedBytes != pos) {
            throw new IOException("could not skip requested number of bytes: " + pos);
        }

        streamPos = pos;
    }
}
