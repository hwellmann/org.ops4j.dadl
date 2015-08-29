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

import java.io.IOException;

import org.ops4j.dadl.io.AbstractBitStreamReader;
import org.ops4j.dadl.io.OutputStreamBitStreamWriter;

/**
 * An adapter for overriding the default (un)marshalling of a given type.
 * <p>
 * Users should implement this interface for any type with non-standard encoding, e.g. to map a
 * complex type to a simple type in a special way.
 *
 * @param <T>
 *            information model type
 *
 * @author hwellmann
 *
 */
public interface DadlAdapter<T> {

    /**
     * Marshals the given info model to the given writer.
     *
     * @param info
     *            information model
     * @param writer
     *            bit stream writer
     * @throws IOException
     *             on write error
     */
    void marshal(T info, OutputStreamBitStreamWriter writer) throws IOException;

    /**
     * Marshals an object of the info model type from the given reader.
     *
     * @param reader
     *            bit stream reader
     * @return value of mapped type
     * @throws IOException
     *             on read error
     */
    T unmarshal(AbstractBitStreamReader reader) throws IOException;
}
