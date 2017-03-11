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
package org.camunda.tngp.broker.logstreams.processor;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.util.StreamUtil;

public class HashIndexSnapshotSupport<T extends HashIndex<?, ?>> implements SnapshotSupport
{
    protected static final int BUFFER_SIZE = 4 * 1024;

    protected static final byte[] READ_BUFFER = new byte[BUFFER_SIZE];
    protected static final ByteBuffer READ_BUFFER_VIEW = ByteBuffer.wrap(READ_BUFFER);

    protected static final byte[] WRITE_BUFFER = new byte[BUFFER_SIZE];
    protected static final ByteBuffer WRITE_BUFFER_VIEW = ByteBuffer.wrap(WRITE_BUFFER);

    protected final T hashIndex;
    protected final IndexStore indexStore;

    public HashIndexSnapshotSupport(T hashIndex, IndexStore indexStore)
    {
        this.hashIndex = hashIndex;
        this.indexStore = indexStore;
    }

    public T getHashIndex()
    {
        return hashIndex;
    }

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
        hashIndex.flush();

        int read = -1;
        int offset = 0;

        do
        {
            read = indexStore.read(READ_BUFFER_VIEW, offset);

            if (read > 0)
            {
                outputStream.write(READ_BUFFER, offset, read);

                offset += read;
            }
        }
        while (read > 0);
    }

    @Override
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        int read = -1;
        int offset = 0;

        do
        {
            read = StreamUtil.read(inputStream, WRITE_BUFFER, offset);

            if (read > 0)
            {
                indexStore.writeFully(WRITE_BUFFER_VIEW, offset);

                offset += read;
            }
        }
        while (read > 0);

        hashIndex.reInit();
    }

    @Override
    public void reset()
    {
        hashIndex.clear();

        READ_BUFFER_VIEW.clear();
        WRITE_BUFFER_VIEW.clear();
    }

}