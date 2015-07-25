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
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.ops4j.dadl.io.ByteArrayBitStreamWriter;

import demo.simple.TextWithLengthField;


/**
 * @author hwellmann
 *
 */
public class StringTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DadlContext dadlContext;

    @Before
    public void before() throws JAXBException {
        dadlContext = DadlContext.newInstance(new File("src/test/resources/simpleModel.xml"));
        dadlContext.setAdapter("varint", new VarIntAdapter());
    }

    @Test
    public void shouldUnmarshalTextWithLengthField() throws IOException {
        ByteArrayBitStreamWriter writer = new ByteArrayBitStreamWriter();
        writer.writeByte(7);
        writer.write("Hamburg".getBytes("UTF-8"));
        writer.close();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes.length, is(8));

        Unmarshaller unmarshaller = dadlContext.createUnmarshaller();
        TextWithLengthField text = unmarshaller.unmarshal(writer.toByteArray(), TextWithLengthField.class);
        assertThat(text.getLen(), is(7));
        assertThat(text.getText(), is("Hamburg"));

    }
}
