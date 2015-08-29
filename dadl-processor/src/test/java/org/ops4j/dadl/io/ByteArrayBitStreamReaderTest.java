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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.Test;

/**
 * @author hwellmann
 *
 */
public class ByteArrayBitStreamReaderTest {

    @Test
    public void shouldReadSignedBigInteger() throws IOException {
        byte[] bytes = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0 };
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(bytes);
        BigInteger n = reader.readSignedBigInteger(40);
        assertThat(n, is(BigInteger.valueOf(-16)));
        reader.close();
    }

    @Test
    public void shouldReadUnsignedByte() throws IOException {
        byte[] bytes = { (byte) 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0 };
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(bytes);
        reader.readByte();
        int value = reader.readUnsignedByte();
        assertThat(value, is(0xFF));
        reader.close();
    }

    @Test
    public void shouldReadUnsignedByteUnaligned() throws IOException {
        byte[] bytes = { (byte) 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0 };
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(bytes);
        reader.skipBits(2);
        int value = reader.readUnsignedByte();
        assertThat(value, is(3));
        reader.close();
    }
}
