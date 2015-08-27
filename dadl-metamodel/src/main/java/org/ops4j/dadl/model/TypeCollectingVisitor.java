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

import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.TaggedSequence;
import org.ops4j.dadl.metamodel.gen.visitor.BaseVisitor;
import org.ops4j.dadl.metamodel.gen.visitor.VisitorAction;

/**
 * @author hwellmann
 *
 */
public class TypeCollectingVisitor extends BaseVisitor {

    private Map<String, DadlType> typeMap = new HashMap<>();

    @Override
    public VisitorAction enter(Enumeration enumeration) {
        Object present = typeMap.putIfAbsent(enumeration.getName(), enumeration);
        if (present != null) {
            throw new IllegalArgumentException("duplicate type " + enumeration.getName());
        }
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(Sequence sequence) {
        Object present = typeMap.putIfAbsent(sequence.getName(), sequence);
        if (present != null) {
            throw new IllegalArgumentException("duplicate type " + sequence.getName());
        }
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(TaggedSequence sequence) {
        Object present = typeMap.putIfAbsent(sequence.getName(), sequence);
        if (present != null) {
            throw new IllegalArgumentException("duplicate type " + sequence.getName());
        }
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(Choice sequence) {
        Object present = typeMap.putIfAbsent(sequence.getName(), sequence);
        if (present != null) {
            throw new IllegalArgumentException("duplicate type " + sequence.getName());
        }
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(SimpleType sequence) {
        Object present = typeMap.putIfAbsent(sequence.getName(), sequence);
        if (present != null) {
            throw new IllegalArgumentException("duplicate type " + sequence.getName());
        }
        return VisitorAction.CONTINUE;
    }

    /**
     * Gets the typeMap.
     * @return the typeMap
     */
    public Map<String, DadlType> getTypeMap() {
        return typeMap;
    }

}
