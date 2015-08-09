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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.visitor.BaseVisitor;
import org.ops4j.dadl.metamodel.gen.visitor.VisitorAction;

/**
 * @author hwellmann
 *
 */
public class TypeCheckingVisitor extends BaseVisitor {


    private Map<String, DadlType> typeMap;
    private Set<String> linked;
    /**
     *
     */
    public TypeCheckingVisitor(Map<String, DadlType> typeMap) {
        this.typeMap = typeMap;
        this.linked = new HashSet<>();
    }

    @Override
    public VisitorAction enter(Element element) {
        linkElement(element);
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(SequenceElement element) {
        linkElement(element);
        return VisitorAction.CONTINUE;
    }

    private void linkElement(Element element) {
        DadlType type = typeMap.get(element.getType());
        if (type == null) {
            throw new IllegalArgumentException("type " + element.getType() + " is undefined");
        }
        linkType(type);
        mergeRepresentationAttributes(element, type);
    }

    @Override
    public VisitorAction enter(SimpleType simpleType) {
        linkType(simpleType);
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(Sequence simpleType) {
        linkType(simpleType);
        return VisitorAction.CONTINUE;
    }

    @Override
    public VisitorAction enter(Choice simpleType) {
        linkType(simpleType);
        return VisitorAction.CONTINUE;
    }

    private void linkType(DadlType type) {
        if (linked.contains(type.getName())) {
            return;
        }
        String baseTypeName = type.getType();
        if (baseTypeName == null) {
            return;
        }
        DadlType baseType = typeMap.get(baseTypeName);
        if (baseType == null) {
            throw new IllegalArgumentException("type " + baseTypeName + " is undefined");
        }
        linkType(baseType);
        mergeRepresentationAttributes(type, baseType);
        linked.add(type.getName());
    }

    private void mergeRepresentationAttributes(DadlType derived, DadlType base) {
        if (derived.getRepresentation() == null) {
            derived.setRepresentation(base.getRepresentation());
        }
        if (derived.getTextPadKind() == null) {
            derived.setTextPadKind(base.getTextPadKind());
        }
        if (derived.getTextTrimKind() == null) {
            derived.setTextTrimKind(base.getTextTrimKind());
        }
        if (derived.getTextStringPadCharacter() == null) {
            derived.setTextStringPadCharacter(base.getTextStringPadCharacter());
        }
        if (derived.getTextStringJustification() == null) {
            derived.setTextStringJustification(base.getTextStringJustification());
        }
        if (derived.getTextNumberPadCharacter() == null) {
            derived.setTextNumberPadCharacter(base.getTextNumberPadCharacter());
        }
        if (derived.getTextNumberJustification() == null) {
            derived.setTextNumberJustification(base.getTextNumberJustification());
        }
        if (derived.getTextNumberRep() == null) {
            derived.setTextNumberRep(base.getTextNumberRep());
        }
        if (derived.getEncoding() == null) {
            derived.setEncoding(base.getEncoding());
        }
        if (derived.getLength() == null) {
            derived.setLength(base.getLength());
        }
        if (derived.getLengthKind() == null) {
            derived.setLengthKind(base.getLengthKind());
        }
        if (derived.getLengthUnit() == null) {
            derived.setLengthUnit(base.getLengthUnit());
        }
    }
}
