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

    protected final byte[] readBuffer = new byte[BUFFER_SIZE];
    protected final ByteBuffer readBufferView = ByteBuffer.wrap(readBuffer);

    protected final byte[] writeBuffer = new byte[BUFFER_SIZE];
    protected final ByteBuffer writeBufferView = ByteBuffer.wrap(writeBuffer);

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
            readBufferView.position(0);
            read = indexStore.read(readBufferView, offset);

            if (read > 0)
            {
                outputStream.write(readBuffer, 0, read);

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
            read = StreamUtil.read(inputStream, writeBuffer, 0);

            if (read > 0)
            {
                writeBufferView.position(0);
                indexStore.writeFully(writeBufferView, offset);

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

        readBufferView.clear();
        writeBufferView.clear();
    }

}