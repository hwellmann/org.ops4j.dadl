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
 * @author hwellmann
 *
 */
public class ByteArrayBitStreamReader extends BitStreamReader {

    private ByteArrayInputStream bais;
    private long length;

    public ByteArrayBitStreamReader(byte[] b) {
        bais = new ByteArrayInputStream(b);
        length = b.length;
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
        return length;
    }

    @Override
    public void seek(long pos) throws IOException {
        checkClosed();
        if (pos < flushedPos) {
            throw new IndexOutOfBoundsException("pos < flushedPos!");
        }
        bitOffset = 0;
        bais.reset();
        bais.skip(pos);
        streamPos = pos;
    }
}
