/*
 * Copyright 2015 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 *
 * See the License for the specific language governing permissions and limitations under the
 * License.
 */
package org.ops4j.dadl.processor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Test;
import org.ops4j.dadl.io.ByteArrayBitStreamReader;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;

import demo.simple.AllNumbers;
import demo.simple.LongNumbers;
import demo.simple.ShortNumbers;

/**
 * @author hwellmann
 *
 */
public class PerformanceTest {

    private static final int NUM_ITERATIONS = 300000;

    private DadlContext dadlContext;

    @Before
    public void before() throws JAXBException {
        dadlContext = DadlContext.newInstance(new File("src/test/resources/simpleModel.xml"));
        dadlContext.setAdapter("varint", new VarIntAdapter());
    }

    @Test
    public void shouldParseAllNumbers() throws IOException, InterruptedException {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeBits(5, 8);
        writer.writeBits(200, 8);
        writer.writeBits(1000, 16);
        writer.writeBits(50000, 16);
        writer.writeBits(5_000_000L, 24);
        writer.writeBits(10_000_000L, 24);
        writer.writeInt(-100000);
        writer.writeUnsignedInt(3_000_000_000L);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(20));

        Unmarshaller parser = dadlContext.createUnmarshaller();
        LocalTime start = LocalTime.now();
        for (int i = 0; i < NUM_ITERATIONS; i++) {
            parser.unmarshal(bytes, AllNumbers.class);
        }
        LocalTime stop = LocalTime.now();
        System.out.println(NUM_ITERATIONS + " iterations with DADL: "
            + start.until(stop, ChronoUnit.MILLIS) + " ms");
    }

    @Test
    public void shouldParseAllNumbersManually() throws IOException {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeBits(5, 8);
        writer.writeBits(200, 8);
        writer.writeBits(1000, 16);
        writer.writeBits(50000, 16);
        writer.writeBits(5_000_000L, 24);
        writer.writeBits(10_000_000L, 24);
        writer.writeInt(-100000);
        writer.writeUnsignedInt(3_000_000_000L);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(20));

        LocalTime start = LocalTime.now();
        for (int i = 0; i < NUM_ITERATIONS; i++) {
            parseAllNumbers(bytes);
        }
        LocalTime stop = LocalTime.now();
        System.out.println(NUM_ITERATIONS + " iterations with BitStreamReader: "
            + start.until(stop, ChronoUnit.MILLIS) + " ms");
    }

    private AllNumbers parseAllNumbers(byte[] bytes) throws IOException {
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(bytes);
        AllNumbers an = new AllNumbers();
        ShortNumbers sn = new ShortNumbers();
        sn.setI8(reader.readByte());
        sn.setU8(reader.readUnsignedByte());
        sn.setI16(reader.readShort());
        sn.setU16(reader.readUnsignedShort());
        LongNumbers ln = new LongNumbers();
        ln.setI24((int) reader.readBits(24));
        ln.setU24((int) reader.readBits(24));
        ln.setI32(reader.readInt());
        ln.setU32(reader.readUnsignedInt());
        reader.close();
        an.setShortNumbers(sn);
        an.setLongNumbers(ln);
        return an;
    }
}
