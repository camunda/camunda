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

import java.util.Iterator;

import org.agrona.DirectBuffer;

import io.zeebe.map.iterator.Bytes2BytesZbMapEntry;
import io.zeebe.map.types.ByteArrayKeyHandler;
import io.zeebe.map.types.ByteArrayValueHandler;

public class Bytes2BytesZbMap extends ZbMap<ByteArrayKeyHandler, ByteArrayValueHandler> implements Iterable<Bytes2BytesZbMapEntry>
{

    protected final ZbMapIterator<ByteArrayKeyHandler, ByteArrayValueHandler, Bytes2BytesZbMapEntry> iterator =
            new ZbMapIterator<>(this, new Bytes2BytesZbMapEntry());

    public Bytes2BytesZbMap(int keyMaxLength, int valueMaxLength)
    {
        super(keyMaxLength, valueMaxLength);
    }

    public Bytes2BytesZbMap(int tableSize, int blocksPerBucket, int keyMaxLength, int valueMaxLength)
    {
        super(tableSize, blocksPerBucket, keyMaxLength, valueMaxLength);
    }

    /**
     * Returns a view on the map value, i.e. direct modification should be avoided.
     * This view may become invalid with the very next interaction with the map.
     * For values shorter than valueMaxLength, the returned buffer contains padding 0s.
     */
    public DirectBuffer get(DirectBuffer key)
    {
        return get(key, 0, key.capacity());
    }

    /**
     * Only wraps the value in the supplied value buffer, does not copy
     */
    public DirectBuffer get(DirectBuffer key, int keyOffset, int keyLength)
    {
        keyHandler.setKey(key, keyOffset, keyLength);
        if (get())
        {
            return valueHandler.getValue();
        }
        else
        {
            return null;
        }
    }

    public boolean put(byte[] key, byte[] value)
    {
        keyHandler.setKey(key);
        valueHandler.setValue(value);
        return put();
    }

    public boolean put(DirectBuffer key, DirectBuffer value)
    {
        return put(key, 0, key.capacity(), value);
    }

    public boolean put(DirectBuffer key, int keyOffset, int keyLength, DirectBuffer value)
    {
        keyHandler.setKey(key, keyOffset, keyLength);
        valueHandler.setValue(value, 0, value.capacity());

        return put();
    }

    public boolean remove(byte[] key)
    {
        keyHandler.setKey(key);
        return remove();
    }

    public boolean remove(DirectBuffer key)
    {
        return remove(key, 0, key.capacity());
    }

    public boolean remove(DirectBuffer key, int offset, int length)
    {
        keyHandler.setKey(key, offset, length);
        return remove();
    }

    @Override
    public Iterator<Bytes2BytesZbMapEntry> iterator()
    {
        iterator.reset();
        return iterator;
    }
}
