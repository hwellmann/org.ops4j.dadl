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
package org.ops4j.dadl.metamodel;

import java.io.File;
import java.nio.file.Paths;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;
import org.ops4j.dadl.generator.JavaModelGenerator;
import org.ops4j.dadl.metamodel.gen.Model;
import org.ops4j.dadl.model.ValidatedModel;

/**
 * @author hwellmann
 *
 */
public class JavaModelGeneratorTest {

    @Test
    public void shouldGenerateJavaModel() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Model.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Model model = (Model) unmarshaller.unmarshal(new File("src/test/resources/simpleModel.xml"));
        ValidatedModel validatedModel = new ValidatedModel(model);
        validatedModel.validate();
        
        JavaModelGenerator generator = new JavaModelGenerator(validatedModel, "demo.simple", Paths.get("target", "out"));
        generator.generateJavaModel();
    }
}
