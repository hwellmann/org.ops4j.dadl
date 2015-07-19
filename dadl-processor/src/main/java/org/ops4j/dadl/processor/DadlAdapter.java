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

import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.io.BitStreamWriter;

/**
 * @author hwellmann
 *
 */
public interface DadlAdapter<T> {

    void marshal(T info, BitStreamWriter writer) throws IOException;
    T unmarshal(BitStreamReader reader) throws IOException;
}
