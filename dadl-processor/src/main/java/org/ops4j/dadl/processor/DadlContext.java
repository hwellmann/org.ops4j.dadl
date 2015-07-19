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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Model;
import org.ops4j.dadl.model.ValidatedModel;

/**
 * @author hwellmann
 *
 */
public class DadlContext {
    
    private ValidatedModel model;
    
    private Map<String, DadlAdapter<?>> adapters = new HashMap<>();
    
    protected DadlContext(ValidatedModel model) {
        this.model = model;
    }
     
    public static DadlContext newInstance(File modelFile) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Model.class);
            javax.xml.bind.Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Model rawModel = (Model) unmarshaller.unmarshal(modelFile);
            ValidatedModel model = new ValidatedModel(rawModel);
            model.validate();
            return new DadlContext(model);
        }
        catch (JAXBException exc) {
            throw new DadlException(exc);
        }
    }
    
    public void setAdapter(String name, DadlAdapter<?> adapter) {
        adapters.put(name, adapter);
    }
    
    public Unmarshaller createUnmarshaller() {
        return new Unmarshaller(this, model);
    }

    public Marshaller createMarshaller() {
        return new Marshaller(this, model);
    }
    
    @SuppressWarnings("unchecked")
    public <T> DadlAdapter<T> getAdapter(DadlType type, Class<T> klass) {
        String adapterName = type.getAdapter();
        if (adapterName == null) {
            return null;
        }
        DadlAdapter<?> adapter = adapters.get(adapterName);
        if (adapter == null) {
            throw new DadlException("no adapter named " + adapterName);
        }
        return (DadlAdapter<T>) adapter;
    }
}
