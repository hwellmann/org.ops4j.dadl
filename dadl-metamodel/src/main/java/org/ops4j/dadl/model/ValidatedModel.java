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
package org.ops4j.dadl.model;

import java.util.HashMap;
import java.util.Map;

import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Model;
import org.ops4j.dadl.metamodel.gen.SequenceElement;

/**
 * A validated DADL model, satisfying additional constraints not expressible in the
 * XML schema.
 *
 * @author hwellmann
 *
 */
public class ValidatedModel {

    private Model model;

    private Map<String, DadlType> typeMap = new HashMap<>();

    /**
     * Creates a validated model for the given raw model. The validation must be executed by
     * invoking the {{@link #validate()} method.
     * @param model raw model
     */
    public ValidatedModel(Model model) {
        this.model = model;
    }

    /**
     * Validates the current model.
     */
    public void validate() {
        TypeCollectingVisitor typeCollectingVisitor = new TypeCollectingVisitor();
        model.accept(typeCollectingVisitor);
        typeMap = typeCollectingVisitor.getTypeMap();
        TypeCheckingVisitor typeCheckingVisitor = new TypeCheckingVisitor(typeMap);
        model.accept(typeCheckingVisitor);
    }

    /**
     * Checks if the given sequence element is optional.
     * @param element sequence element
     * @return true if optional
     */
    public boolean isOptional(SequenceElement element) {
        return element.getMinOccurs() == 0 && element.getMaxOccurs() == 1;
    }

    /**
     * Checks if the given sequence element is a list.
     * @param element sequence element
     * @return true if list
     */
    public boolean isList(SequenceElement element) {
        return element.getOccursCount() != null || element.getMaxOccurs() > 1;
    }

    /**
     * Gets the DADL type with the given name.
     * @param typeName type name
     * @return DADL type, or null
     */
    public DadlType getType(String typeName) {
        return typeMap.get(typeName);
    }

    /**
     * Gets the raw model.
     *
     * @return the model
     */
    public Model getModel() {
        return model;
    }

    /**
     * Gets the type map.
     *
     * @return map of type names to types
     */
    public Map<String, DadlType> getTypeMap() {
        return typeMap;
    }
}
