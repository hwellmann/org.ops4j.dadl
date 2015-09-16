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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.ops4j.dadl.io.ByteArrayBitStreamReader;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;

import demo.simple.AllNumbers;
import demo.simple.BcdSequence;
import demo.simple.BitField;
import demo.simple.ChoiceWithDiscriminator;
import demo.simple.Colour;
import demo.simple.DecimalNumbers;
import demo.simple.LongNumbers;
import demo.simple.MyChoice;
import demo.simple.NumberList;
import demo.simple.NumberWithColour;
import demo.simple.OpaqueContainer;
import demo.simple.Option2;
import demo.simple.PaddedInner;
import demo.simple.PaddedOuter;
import demo.simple.ParsedNumberList;
import demo.simple.SeqMinLength;
import demo.simple.SeqMinLengthSuffix;
import demo.simple.SequenceWithOptional;
import demo.simple.ShortNumbers;
import demo.simple.TaggedString;

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
    public void shouldUnmarshalChoice() throws Exception {
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
    public void shouldUnmarshallSequenceWithOptionalElements() throws IOException {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeBits(0x0B, 8);
        writer.writeBits(7, 8);
        writer.writeBits(42, 24);
        writer.writeBits(12345678, 32);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(9));

        Unmarshaller parser = dadlContext.createUnmarshaller();

        SequenceWithOptional choice = parser.unmarshal(bytes, SequenceWithOptional.class);
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
        assertThat(reader.readUnsignedByte(), is(0x0B));
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

    @Test
    public void shouldUnmarshalParsedArray() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeInt(16);
        writer.writeInt(25);
        writer.writeInt(36);
        writer.writeInt(49);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(16));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        ParsedNumberList numberList = unmarshaller.unmarshal(writer.toByteArray(),
            ParsedNumberList.class);
        assertThat(numberList.getItems(), contains(16, 25, 36, 49));
    }

    @Test
    public void shouldUnmarshalChoiceWithDiscriminator() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeBits(40, 8);
        writer.writeBits(42, 24);
        writer.writeBits(12345678, 32);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(8));

        Unmarshaller parser = dadlContext.createUnmarshaller();

        ChoiceWithDiscriminator choice = parser.unmarshal(bytes, ChoiceWithDiscriminator.class);
        assertThat(choice, is(notNullValue()));
        assertThat(choice.getOpt3(), is(nullValue()));
        assertThat(choice.getOpt4(), is(notNullValue()));
        assertThat(choice.getOpt4().getI41(), is(42));
        assertThat(choice.getOpt4().getI42(), is(12345678));
    }

    @Test
    public void shouldUnmarshalDecimalNumbers() throws Exception {
        String marshalled = "005612";
        Unmarshaller parser = dadlContext.createUnmarshaller();
        DecimalNumbers numbers = parser.unmarshal(marshalled.getBytes(), DecimalNumbers.class);
        assertThat(numbers, is(notNullValue()));
        assertThat(numbers.getD1(), is(56));
        assertThat(numbers.getD2(), is(12));
    }

    @Test
    public void shouldMarshalDecimalNumbers() throws Exception {
        DecimalNumbers numbers = new DecimalNumbers();
        numbers.setD1(56);
        numbers.setD2(12);

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(numbers, os);
        String marshalled = os.toString();
        assertThat(marshalled, is("005612"));
    }

    @Test
    public void shouldUnmarshalNumberWithColour() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeByte(22);
        writer.writeByte(14);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(2));

        Unmarshaller parser = dadlContext.createUnmarshaller();
        NumberWithColour nwc = parser.unmarshal(bytes, NumberWithColour.class);
        assertThat(nwc, is(notNullValue()));
        assertThat(nwc.getI1(), is(22));
        assertThat(nwc.getC(), is(Colour.YELLOW));
    }

    @Test
    public void shouldMarshalNumberWithColour() throws Exception {
        NumberWithColour nwc = new NumberWithColour();
        nwc.setI1(99);
        nwc.setC(Colour.GREEN);
        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(nwc, os);
        assertThat(os.toByteArray().length, is(2));

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readByte(), is((byte) 99));
        assertThat(reader.readByte(), is((byte) 17));
        reader.close();
    }

    @Test
    public void shouldUnmarshalOpaqueContainer() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeInt(4);
        writer.writeByte(20);
        writer.writeByte(22);
        writer.writeByte(24);
        writer.writeByte(26);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(8));

        Unmarshaller parser = dadlContext.createUnmarshaller();
        OpaqueContainer oc = parser.unmarshal(bytes, OpaqueContainer.class);
        assertThat(oc, is(notNullValue()));
        assertThat(oc.getLength(), is(4));
        assertThat(oc.getContent().length, is(oc.getLength()));
        assertThat(oc.getContent()[0], is((byte) 20));
        assertThat(oc.getContent()[1], is((byte) 22));
        assertThat(oc.getContent()[2], is((byte) 24));
        assertThat(oc.getContent()[3], is((byte) 26));
    }

    @Test
    public void shouldMarshalOpaqueContainer() throws Exception {
        OpaqueContainer oc = new OpaqueContainer();
        oc.setContent("DADL".getBytes());
        oc.setLength(oc.getContent().length);

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(oc, os);
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readInt(), is(4));
        assertThat(reader.readByte(), is((byte) 'D'));
        assertThat(reader.readByte(), is((byte) 'A'));
        assertThat(reader.readByte(), is((byte) 'D'));
        assertThat(reader.readByte(), is((byte) 'L'));
        reader.close();
    }

    @Test
    public void shouldUnmarshalTaggedString() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        String text = "Hello DADL!";
        writer.writeByte(0x0A);
        writer.writeByte(2 + text.length());
        writer.writeByte(22);
        writer.writeByte(14);
        writer.writeBytes(text);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(15));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        TaggedString taggedString = unmarshaller.unmarshal(bytes, TaggedString.class);
        assertThat(taggedString.getText(), is(text));
    }

    @Test
    public void shouldMarshalTaggedString() throws Exception {
        NumberWithColour nwc = new NumberWithColour();
        nwc.setI1(22);
        nwc.setC(Colour.GREEN);
        String text = "Hello DADL!";
        TaggedString taggedString = new TaggedString();
        taggedString.setNwc(nwc);
        taggedString.setText(text);

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(taggedString, os);

        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readByte(), is((byte)0x0A));
        assertThat(reader.readByte(), is((byte)(2 + text.length())));
        assertThat(reader.readByte(), is((byte) 22));
        assertThat(reader.readByte(), is((byte) 17));
        byte[] bytes = new byte[11];
        reader.read(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8), is(text));
        reader.close();
    }

    @Test
    public void shouldUnmarshalBcdSequence() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeShort(4096);
        writer.writeBits(1, 4);
        writer.writeBits(2, 4);
        writer.writeBits(3, 4);
        writer.writeBits(4, 4);
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(4));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        BcdSequence bcdSequence = unmarshaller.unmarshal(bytes, BcdSequence.class);
        assertThat(bcdSequence.getI16(), is(4096));
        assertThat(bcdSequence.getBcd(), is(1234));
    }

    @Test
    public void shouldMarshalBcdSequence() throws Exception {
        BcdSequence bcdSequence = new BcdSequence();
        bcdSequence.setI16(9999);
        bcdSequence.setBcd(2011);
        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(bcdSequence, os);
        assertThat(os.toByteArray().length, is(4));
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readShort(), is((short) 9999));
        assertThat(reader.readByte(), is((byte) 0x20));
        assertThat(reader.readByte(), is((byte) 0x11));
        reader.close();
    }

    @Test
    public void shouldMarshalSeqMinLength() throws Exception {
        SeqMinLength sml = new SeqMinLength();
        NumberList list = new NumberList();
        list.getItems().add(17);
        list.getItems().add(39);
        sml.setNumberList(list);
        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(sml, os);
        assertThat(os.toByteArray().length, is(20));
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readByte(), is((byte) 2));
        assertThat(reader.readInt(), is(17));
        assertThat(reader.readInt(), is(39));
        reader.close();
    }

    @Test
    public void shouldMarshalSeqMinLengthNoPadding() throws Exception {
        SeqMinLength sml = new SeqMinLength();
        NumberList list = new NumberList();
        list.getItems().add(17);
        list.getItems().add(39);
        list.getItems().add(23);
        list.getItems().add(12);
        list.getItems().add(92);
        sml.setNumberList(list);
        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(sml, os);
        assertThat(os.toByteArray().length, is(21));
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readByte(), is((byte) 5));
        assertThat(reader.readInt(), is(17));
        assertThat(reader.readInt(), is(39));
        assertThat(reader.readInt(), is(23));
        assertThat(reader.readInt(), is(12));
        assertThat(reader.readInt(), is(92));
        reader.close();
    }

    @Test
    public void shouldUnmarshalSeqMinLengthSuffix() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeByte(2);
        writer.writeInt(17);
        writer.writeInt(39);
        for (int i = 0; i < 11; i++) {
            writer.write(0);
        }
        writer.writeByte(99);
        writer.close();

        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(21));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        SeqMinLengthSuffix seqMinLength = unmarshaller.unmarshal(bytes, SeqMinLengthSuffix.class);
        NumberList list = seqMinLength.getSml().getNumberList();
        assertThat(list.getNumItems(), is(2));
        assertThat(list.getItems().get(0), is(17));
        assertThat(list.getItems().get(1), is(39));
        assertThat(seqMinLength.getSuffix(), is(99));
    }

    @Test
    public void shouldMarshalBitField() throws Exception {
        BitField bitField = new BitField();
        bitField.setB2(3);
        bitField.setB3(6);
        bitField.setB4(11);
        bitField.setB7(125);

        Marshaller marshaller = dadlContext.createMarshaller();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.marshal(bitField, os);
        assertThat(os.toByteArray().length, is(2));
        ByteArrayBitStreamReader reader = new ByteArrayBitStreamReader(os.toByteArray());
        assertThat(reader.readBits(2), is(3L));
        assertThat(reader.readBits(3), is(6L));
        assertThat(reader.readBits(4), is(11L));
        assertThat(reader.readBits(7), is(125L));
        reader.close();
    }

    @Test
    public void shouldUnmarshalBitField() throws Exception {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeBits(3, 2);
        writer.writeBits(6, 3);
        writer.writeBits(11, 4);
        writer.writeBits(125, 7);
        writer.close();

        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(2));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        BitField bitField = unmarshaller.unmarshal(bytes, BitField.class);
        assertThat(bitField.getB2(), is(3));
        assertThat(bitField.getB3(), is(6));
        assertThat(bitField.getB4(), is(11));
        assertThat(bitField.getB7(), is(125));
    }

}
