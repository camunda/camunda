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
package io.zeebe.logstreams.snapshot;

import java.io.InputStream;
import java.io.OutputStream;

import io.zeebe.logstreams.spi.ComposableSnapshotSupport;
import io.zeebe.map.ZbMap;
import io.zeebe.map.ZbMapSerializer;

public class ZbMapSnapshotSupport<T extends ZbMap<?, ?>> implements ComposableSnapshotSupport
{
    private final T zbMap;

    private final ZbMapSerializer indexSerializer = new ZbMapSerializer();

    public ZbMapSnapshotSupport(T zbMap)
    {
        this.zbMap = zbMap;
        this.indexSerializer.wrap(zbMap);
    }

    public T getZbMap()
    {
        return zbMap;
    }

    public long snapshotSize()
    {
        return indexSerializer.serializationSize();
    }

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
        indexSerializer.writeToStream(outputStream);
    }

    @Override
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        indexSerializer.readFromStream(inputStream);
    }

    @Override
    public void reset()
    {
        zbMap.clear();
    }

}
