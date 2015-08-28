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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.ops4j.dadl.exc.DadlException;
import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.io.BitStreamWriter;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Model;
import org.ops4j.dadl.model.ValidatedModel;

/**
 * Entry point for client applications.
 * <p>
 * {@link #newInstance(File)} is used to build a DADL context for a given model. This context is
 * then used to construct marshallers or unmarshallers.
 *
 * @author hwellmann
 *
 */
public class DadlContext {

    private ValidatedModel model;

    private Map<String, DadlAdapter<?>> adapters = new HashMap<>();

    protected DadlContext(ValidatedModel model) {
        this.model = model;
    }

    /**
     * Creates a DADL context for a XML model file.
     *
     * @param modelFile
     *            file containing a XML model satisfying the DADL schema
     * @return DADL context
     */
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

    /**
     * Sets an adapter with the given name.
     *
     * @param name
     *            adapter name, referenced in the XML model
     * @param adapter
     *            adapter implementation
     */
    public void setAdapter(String name, DadlAdapter<?> adapter) {
        adapters.put(name, adapter);
    }

    /**
     * Creates an unmarshaller for the current model.
     *
     * @return unmarshaller
     */
    public Unmarshaller createUnmarshaller() {
        return new Unmarshaller(this, model);
    }

    /**
     * Creates a marshaller for the current model.
     *
     * @return marshaller
     */
    public Marshaller createMarshaller() {
        return new Marshaller(this, model);
    }

    /**
     * Gets an adapter for the given DADL type and the given Java model class.
     *
     * @param type
     *            DADL type
     * @param klass
     *            Java model class
     * @return adapter, or null if no adapter is defined for this type
     */
    @SuppressWarnings("unchecked")
    public <T> DadlAdapter<T> getAdapter(DadlType type) {
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


    <T> T readValueViaAdapter(DadlType type, Class<T> klass, BitStreamReader reader)
        throws IOException {
        DadlAdapter<T> adapter = getAdapter(type);
        if (adapter == null) {
            return null;
        }
        return adapter.unmarshal(reader);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    boolean writeValueViaAdapter(DadlType type, Object info, BitStreamWriter writer)
        throws IOException {
        DadlAdapter adapter = getAdapter(type);
        if (adapter == null) {
            return false;
        }
        else {
            adapter.marshal(info, writer);
            return true;
        }
    }



}
