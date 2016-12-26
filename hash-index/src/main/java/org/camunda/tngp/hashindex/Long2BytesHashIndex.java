/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.hashindex;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.hashindex.types.ByteArrayValueHandler;
import org.camunda.tngp.hashindex.types.LongKeyHandler;

/**
 * {@link HashIndex} that maps Long keys to Byte Array values. All values have a
 * fixed size which is defined on creation.
 */
public class Long2BytesHashIndex extends HashIndex<LongKeyHandler, ByteArrayValueHandler>
{

    public Long2BytesHashIndex(IndexStore indexStore, int indexSize, int blockLength, int valueLength)
    {
        super(indexStore, LongKeyHandler.class, ByteArrayValueHandler.class, indexSize, blockLength, SIZE_OF_LONG, valueLength);
    }

    public Long2BytesHashIndex(IndexStore indexStore)
    {
        super(indexStore, LongKeyHandler.class, ByteArrayValueHandler.class);
    }

    public byte[] get(long key)
    {
        keyHandler.theKey = key;
        final boolean found = get();
        return found ? valueHandler.theValue : null;
    }

    public boolean put(long key, byte[] value)
    {
        checkValueLength(value);

        keyHandler.theKey = key;
        valueHandler.theValue = value;
        return put();
    }

    public byte[] remove(long key)
    {
        keyHandler.theKey = key;
        final boolean removed = remove();
        return removed ? valueHandler.theValue : null;
    }

    protected void checkValueLength(byte[] value)
    {
        final int length = value.length;
        if (length > recordValueLength())
        {
            throw new IllegalArgumentException("Illegal byte array length: expected at most " + recordValueLength() + ", but was " + length);
        }
    }

}
