/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import java.io.InputStream;
import java.io.OutputStream;

import io.zeebe.hashindex.HashIndex;
import io.zeebe.hashindex.IndexSerializer;
import io.zeebe.logstreams.spi.SnapshotSupport;

public class HashIndexSnapshotSupport<T extends HashIndex<?, ?>> implements SnapshotSupport
{
    private final T hashIndex;

    private final IndexSerializer indexSerializer = new IndexSerializer();

    public HashIndexSnapshotSupport(T hashIndex)
    {
        this.hashIndex = hashIndex;
        this.indexSerializer.wrap(hashIndex);
    }

    public T getHashIndex()
    {
        return hashIndex;
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
        hashIndex.clear();
    }

}