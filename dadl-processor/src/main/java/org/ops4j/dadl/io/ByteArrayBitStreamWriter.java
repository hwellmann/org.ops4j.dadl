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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.ops4j.dadl.exc.Exceptions;

/**
 * A bit stream writer writing to a byte array.
 *
 * @author hwellmann
 *
 */
public class ByteArrayBitStreamWriter extends OutputStreamBitStreamWriter {

    private boolean isClosed;

    /**
     * Creates a new writer.
     */
    public ByteArrayBitStreamWriter() {
        super(new ByteArrayOutputStream());
    }

    @Override
    protected ByteArrayOutputStream getStream() {
        return (ByteArrayOutputStream) super.getStream();
    }

    /**
     * Closes the writer and returns the underlying byte array.
     * @return byte array
     */
    public byte[] toByteArray() {
        try {
            close();
        }
        catch (IOException exc) {
            throw Exceptions.unchecked(exc);
        }
        return getStream().toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            super.close();
        }
    }
}
