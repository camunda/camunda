/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.map.types.ByteArrayKeyHandler;
import io.zeebe.map.types.ByteArrayValueHandler;

public class Bytes2BytesZbMap extends ZbMap<ByteArrayKeyHandler, ByteArrayValueHandler>
{

    protected final int keyMaxLength;
    protected final int valueMaxLength;

    public Bytes2BytesZbMap(int keyMaxLength, int valueMaxLength)
    {
        super(keyMaxLength, valueMaxLength);
        this.keyMaxLength = keyMaxLength;
        this.valueMaxLength = valueMaxLength;
    }

    public Bytes2BytesZbMap(int tableSize, int blocksPerBucket, int keyMaxLength, int valueMaxLength)
    {
        super(tableSize, blocksPerBucket, keyMaxLength, valueMaxLength);
        this.keyMaxLength = keyMaxLength;
        this.valueMaxLength = valueMaxLength;
    }

    public boolean get(byte[] key, byte[] value)
    {
        ensureKeyMaxLength(key.length);
        ensureValueMaxLength(value.length);

        keyHandler.setKey(key);
        valueHandler.setValue(value);
        return get();
    }

    public boolean get(DirectBuffer key, MutableDirectBuffer value)
    {
        ensureKeyMaxLength(key.capacity());
        ensureValueMaxLength(value.capacity());

        keyHandler.setKey(key, 0, key.capacity());
        valueHandler.setValue(value, 0, value.capacity());

        return get();
    }

    public boolean put(byte[] key, byte[] value)
    {
        ensureKeyMaxLength(key.length);
        ensureValueMaxLength(value.length);

        keyHandler.setKey(key);
        valueHandler.setValue(value);
        return put();
    }

    public boolean put(DirectBuffer key, DirectBuffer value)
    {
        ensureKeyMaxLength(key.capacity());
        ensureValueMaxLength(value.capacity());

        keyHandler.setKey(key, 0, key.capacity());
        valueHandler.setValue(value, 0, value.capacity());

        return put();
    }

    public boolean remove(byte[] key)
    {
        ensureKeyMaxLength(key.length);

        keyHandler.setKey(key);
        return remove();
    }

    public boolean remove(DirectBuffer key)
    {
        ensureKeyMaxLength(key.capacity());

        keyHandler.setKey(key, 0, key.capacity());
        return remove();
    }

    private void ensureValueMaxLength(int valueLength)
    {
        if (valueLength > valueMaxLength)
        {
            throw new IllegalArgumentException(String.format("Value exceeds max value length. Max value length is %d, got %d", valueMaxLength, valueLength));
        }
    }

    private void ensureKeyMaxLength(int keyLength)
    {
        if (keyLength > keyMaxLength)
        {
            throw new IllegalArgumentException(String.format("Value exceeds max value length. Max value length is %d, got %d", keyMaxLength, keyLength));
        }
    }
}
