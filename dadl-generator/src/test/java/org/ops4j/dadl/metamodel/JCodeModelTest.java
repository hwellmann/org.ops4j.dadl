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
import java.util.List;

import org.junit.Test;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JVar;

/**
 * @author hwellmann
 *
 */
public class JCodeModelTest {
    
    @Test
    public void shouldWritePojo() throws Exception {
        JCodeModel codeModel = new JCodeModel();
        JPackage pkg = codeModel._package("demo.pojo");
        JDefinedClass person = pkg._class("Person");
        JFieldVar firstNameField = person.field(JMod.PRIVATE, String.class, "firstName");
        
        
        JMethod getter = person.method(JMod.PUBLIC, String.class, "getFirstName");
        getter.body()._return(firstNameField);
        
        JMethod setter = person.method(JMod.PUBLIC, codeModel.VOID, "setFirstName");
        JVar p1 = setter.param(String.class, "firstName");
        setter.body().assign(JExpr._this().ref("firstName"), p1);

        JClass listOfString = codeModel.ref(List.class).narrow(String.class);
        JFieldVar phoneNumbersField = person.field(JMod.PRIVATE, listOfString, "phoneNumbers");
        
        JMethod phoneNumbersGetter = person.method(JMod.PUBLIC, listOfString, "getPhoneNumbers");
        phoneNumbersGetter.body()._return(phoneNumbersField);
        
        
        

        person.field(JMod.PRIVATE, String.class, "lastName");
        
        File outputDir = new File("target/out");
        outputDir.mkdirs();
        codeModel.build(outputDir);
    }

}
