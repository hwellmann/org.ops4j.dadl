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
package org.ops4j.dadl.processor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.ops4j.dadl.io.ByteArrayBitStreamReader;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;

import demo.simple.AllNumbers;
import demo.simple.LongNumbers;
import demo.simple.MyChoice;
import demo.simple.NumberList;
import demo.simple.Option2;
import demo.simple.PaddedInner;
import demo.simple.PaddedOuter;
import demo.simple.ShortNumbers;


/**
 * @author hwellmann
 *
 */
public class AllNumbersTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DadlContext dadlContext;

    @Before
    public void before() throws JAXBException {
        dadlContext = DadlContext.newInstance(new File("src/test/resources/simpleModel.xml"));
        dadlContext.setAdapter("varint", new VarIntAdapter());
    }

    @Test
    public void shouldMarshalAllNumbers() throws IOException {
        ShortNumbers sn = new ShortNumbers();
        sn.setI8((byte) 5);
        sn.setU8((short) 200);
        sn.setI16((short) 1000);
        sn.setU16(50000);

        LongNumbers ln = new LongNumbers();
        ln.setI32(-100000);
        ln.setU32(3_000_000_000L);
        ln.setI24(5_000_000);
        ln.setU24(10_000_000);

        AllNumbers an = new AllNumbers();
        an.setShortNumbers(sn);
        an.setLongNumbers(ln);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Marshaller marshaller = dadlContext.createMarshaller();
        marshaller.marshal(an, os);
        byte[] bytes = os.toByteArray();
        assertThat(bytes.length, is(20));

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(bytes);
        assertThat(reader.readByte(), is((byte) 5));
        assertThat(reader.readUnsignedByte(), is(200));
        assertThat(reader.readShort(), is((short) 1000));
        assertThat(reader.readUnsignedShort(), is(50000));
        assertThat(reader.readBits(24), is(5_000_000L));
        assertThat(reader.readBits(24), is(10_000_000L));
        assertThat(reader.readInt(), is(-100000));
        assertThat(reader.readUnsignedInt(), is(3_000_000_000L));
        reader.close();
    }

    @Test
    public void shouldParseAllNumbers() throws IOException {
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
        AllNumbers an = parser.unmarshal(bytes, AllNumbers.class);
        assertThat(an, is(notNullValue()));
        ShortNumbers sn = an.getShortNumbers();
        assertThat(sn, is(notNullValue()));
        assertThat(sn.getI8(), is(5));
        assertThat(sn.getU8(), is(200));
        assertThat(sn.getI16(), is(1000));
        assertThat(sn.getU16(), is(50000));

        LongNumbers ln = an.getLongNumbers();
        assertThat(ln, is(notNullValue()));
        assertThat(ln.getI24(), is(5_000_000));
        assertThat(ln.getU24(), is(10_000_000));
        assertThat(ln.getI32(), is(-100_000));
        assertThat(ln.getU32(), is(3_000_000_000L));
    }

    @Test
    public void shouldUnmarshalChoice() throws Exception{
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeBits(0x0B, 8);
        writer.writeBits(7, 8);
        writer.writeBits(42, 24);
        writer.writeBits(12345678, 32);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(9));

        Unmarshaller parser = dadlContext.createUnmarshaller();

        MyChoice choice = parser.unmarshal(bytes, MyChoice.class);
        assertThat(choice, is(notNullValue()));
        assertThat(choice.getOpt1(), is(nullValue()));
        assertThat(choice.getOpt2(), is(notNullValue()));
        assertThat(choice.getOpt2().getI21(), is(42));
        assertThat(choice.getOpt2().getI22(), is(12345678));
    }

    @Test
    public void shouldMarshalChoice() throws Exception {
        Option2 opt2 = new Option2();
        opt2.setI21(42);
        opt2.setI22(12345678);
        MyChoice myChoice = new MyChoice();
        myChoice.setOpt2(opt2);

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(myChoice, os);

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readUnsignedByte(), is (0x0B));
        assertThat(reader.readUnsignedByte(), is(7));
        assertThat(reader.readBits(24), is(42L));
        assertThat(reader.readBits(32), is(12345678L));
        reader.close();
    }

    @Test
    public void shouldMarshalList() throws Exception {
        NumberList numberList = new NumberList();
        numberList.setNumItems(3);
        numberList.getItems().addAll(Arrays.asList(16, 25, 36));

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(numberList, os);
        assertThat(os.toByteArray().length, is(13));

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readUnsignedByte(), is(3));
        assertThat(reader.readInt(), is(16));
        assertThat(reader.readInt(), is(25));
        assertThat(reader.readInt(), is(36));
        reader.close();
    }

    @Test
    public void shouldUnmarshalList() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeByte(3);
        writer.writeInt(16);
        writer.writeInt(25);
        writer.writeInt(36);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(13));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        NumberList numberList = unmarshaller.unmarshal(writer.toByteArray(), NumberList.class);
        assertThat(numberList.getNumItems(), is(3));
        assertThat(numberList.getItems(), contains(16, 25, 36));
    }

    @Test
    public void shouldIgnoreMemberWhenOutputValueCalc() throws Exception {
        NumberList numberList = new NumberList();
        numberList.getItems().addAll(Arrays.asList(16, 25, 36));

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(numberList, os);
        assertThat(os.toByteArray().length, is(13));

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readUnsignedByte(), is(3));
        assertThat(reader.readInt(), is(16));
        assertThat(reader.readInt(), is(25));
        assertThat(reader.readInt(), is(36));
        reader.close();
    }

    @Test
    public void shouldMarshalWithPadding() throws Exception {
        PaddedInner inner = new PaddedInner();
        inner.setA(12);
        inner.setB(34);
        PaddedOuter outer = new PaddedOuter();
        outer.setInner(inner);
        outer.setC(56);

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(outer, os);
        assertThat(os.toByteArray().length, is(11));

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readShort(), is((short) 12));
        assertThat(reader.readShort(), is((short) 34));
        reader.skipBytes(5);
        assertThat(reader.readShort(), is((short) 56));
        reader.close();
    }
}
