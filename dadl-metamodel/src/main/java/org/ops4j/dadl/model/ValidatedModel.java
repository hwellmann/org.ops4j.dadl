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
 * @author hwellmann
 *
 */
public class ValidatedModel {

    private Model model;

    private Map<String, DadlType> typeMap = new HashMap<>();

    public ValidatedModel(Model model) {
        this.model = model;
    }

    public void validate() {
        TypeCollectingVisitor typeCollectingVisitor = new TypeCollectingVisitor();
        model.accept(typeCollectingVisitor);
        typeMap = typeCollectingVisitor.getTypeMap();
        TypeCheckingVisitor typeCheckingVisitor = new TypeCheckingVisitor(typeMap);
        model.accept(typeCheckingVisitor);
    }

    public boolean isOptional(SequenceElement element) {
        return element.getMinOccurs() == 0 && element.getMaxOccurs() == 1;
    }

    public boolean isList(SequenceElement element) {
        return element.getOccursCount() != null || element.getMaxOccurs() > 1;
    }

    public DadlType getType(String typeName) {
        return typeMap.get(typeName);
    }

    /**
     * Gets the model.
     * 
     * @return the model
     */
    public Model getModel() {
        return model;
    }

    /**
     * Gets the typeMap.
     * 
     * @return the typeMap
     */
    public Map<String, DadlType> getTypeMap() {
        return typeMap;
    }

}
