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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Stack;

import javax.el.CompositeELResolver;
import javax.el.ELManager;
import javax.el.ELProcessor;
import javax.el.PropertyNotFoundException;
import javax.el.StandardELContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import demo.simple.ShortNumbers;

/**
 * @author hwellmann
 *
 */
public class ELProcessorTest {
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    @Test
    public void shouldGetScalar() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("s", "Hello");
        processor.defineBean("i", 42);
        processor.setValue("t", "Text");
        String text = (String) processor.getValue("t", String.class);
        assertThat(text, is("Text"));
        assertThat(processor.eval("i"), is(42));
        
        
        ShortNumbers sn = new ShortNumbers();
        sn.setI8(5);
        sn.setU8(200);
        sn.setI16(1000);
        sn.setU16(50000);

        processor.setValue("sn", sn);
        assertThat(processor.eval("sn.u8"), is(200));
        processor.setValue("sn.u8", 123);
        assertThat(processor.eval("sn.u8"), is(123));

        processor.setVariable("$", "sn.u8");
        processor.setValue("$", 45);
        assertThat(processor.eval("sn.u8"), is(45));
        
        processor.defineBean("snb", sn);
        assertThat(processor.eval("snb.u8"), is(45));
        assertThat(processor.eval("i == 42"), is(true));
    }
    
    @Test
    public void shouldClearValue() {
        ELProcessor processor = new ELProcessor();
        processor.setValue("t", "Text");
        assertThat(processor.getValue("t", String.class), is("Text"));
        processor.setValue("t", null);
        assertThat(processor.getValue("t", Object.class), is(nullValue()));
        assertThat(processor.getValue("t", String.class), is(""));
        assertThat(processor.eval("t"), is(nullValue()));
    }

    @Test
    public void shouldCloneContext() {
        ELProcessor processor = new ELProcessor();
        processor.setValue("t", "Text");
        ELManager manager = processor.getELManager();
        StandardELContext context = manager.getELContext();
        StandardELContext newContext = new StandardELContext(manager.getExpressionFactory());
        manager.setELContext(newContext);
        CompositeELResolver composite = new CompositeELResolver();
        composite.add(context.getELResolver());
        newContext.addELResolver(composite);
        assertThat(processor.getValue("t", String.class), is("Text"));
        processor.setValue("n", 99);
        assertThat(processor.eval("n"), is(99));
        manager.setELContext(context);
        
        thrown.expect(PropertyNotFoundException.class);
        processor.eval("n");        
    }

    @Test
    public void shouldManageContextStack() {
        ELProcessor processor = new ELProcessor();
        Stack<String> levels = new Stack<>();
        levels.push("Level3");
        levels.push("Level2");
        levels.push("Level1");
        processor.setValue("up", levels);
        assertThat(processor.eval("up[1]"), is("Level2"));
        levels.insertElementAt("Level4", 0);
        assertThat(processor.eval("up[1]"), is("Level3"));
        assertThat(processor.eval("up[2]"), is("Level2"));
        levels.remove(0);
        assertThat(processor.eval("up[1]"), is("Level2"));
    }    
}
