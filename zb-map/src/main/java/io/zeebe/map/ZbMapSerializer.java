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

import static org.agrona.BitUtil.SIZE_OF_INT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Can read / write map from / to stream
 *
 */
public class ZbMapSerializer
{
    /**
     * The version of the snapshot.
     * Part of the metadata header, which will be written on every snapshot.
     */
    private static final int VERSION = 1;

    /**
     * The size of the header, which contains meta data like the version of the snapshot etc.
     */
    private static final int METADATA_LEN = SIZE_OF_INT;

    private final byte[] buffer = new byte[IoUtil.BLOCK_SIZE];
    private final UnsafeBuffer bufferView = new UnsafeBuffer(buffer);

    private ZbMap<?, ?> map;

    public void wrap(ZbMap<?, ?> map)
    {
        this.map = map;
    }

    public long serializationSize()
    {
        return map.size() + METADATA_LEN;
    }

    public void writeToStream(OutputStream outputStream) throws IOException
    {
        bufferView.putInt(0, VERSION);
        outputStream.write(buffer, 0, SIZE_OF_INT);

        map.getHashTable().writeToStream(outputStream, buffer);
        map.getBucketBufferArray().writeToStream(outputStream, buffer);
    }

    public void readFromStream(InputStream inputStream) throws IOException
    {
        final int bytesRead = inputStream.read(buffer, 0, SIZE_OF_INT);

        if (bytesRead < SIZE_OF_INT)
        {
            throw new IOException("Unable to read map snapshot version");
        }

        final int version = bufferView.getInt(0);

        if (version != VERSION)
        {
            throw new RuntimeException(String.format("Cannot read map snapshot: expected version %d but got version %d", VERSION, version));
        }

        map.getHashTable().readFromStream(inputStream, buffer);
        map.getBucketBufferArray().readFromStream(inputStream, buffer);
    }

}
