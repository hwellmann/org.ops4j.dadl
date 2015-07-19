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
public class VarIntAdapter implements DadlAdapter<Long> {

    @Override
    public void marshal(Long info, BitStreamWriter writer) throws IOException {
        if (info < 0) {
            throw new MarshalException("value must not be negative: " + info);
        }
        if (info < 128) {
            writer.writeBits(info, 8);
        }
        else if (info < 0x0100) {
            writer.writeByte(0x81);
            writer.writeBits(info, 8);
        }
        else if (info < 0x01_0000) {
            writer.writeByte(0x82);
            writer.writeBits(info, 16);
        }
        else if (info < 0x0100_0000) {
            writer.writeByte(0x83);
            writer.writeBits(info, 24);
        }
        else if (info < 0x01_0000_0000L) {
            writer.writeByte(0x84);
            writer.writeBits(info, 32);
        }
        else {
            throw new IllegalArgumentException("tag requires more than 32 bits: " + info);
        }
    }

    @Override
    public Long unmarshal(BitStreamReader reader) throws IOException {
        long value = reader.readUnsignedByte();
        if (value < 128) {
            return value;
        }
        long numBytes = value ^ 0x80;
        if (numBytes > 4) {
            throw new UnmarshalException("illegal number of bytes: " + numBytes);
        }
        return reader.readBits((int) numBytes * 8);
    }
}
