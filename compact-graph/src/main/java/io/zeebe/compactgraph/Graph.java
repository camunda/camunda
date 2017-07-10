/**
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
package io.zeebe.compactgraph;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static io.zeebe.compactgraph.GraphMetadataDescriptor.*;

public class Graph
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    protected int nodeCount;
    protected int dataOffset;
    protected int dataLength;
    protected int metatdataLength;

    public Graph wrap(byte[] buffer)
    {
        this.buffer.wrap(buffer);
        init();
        return this;
    }

    public Graph wrap(DirectBuffer src, int offset, int lenght)
    {
        this.buffer.wrap(src, offset, lenght);
        init();
        return this;
    }

    protected void init()
    {
        nodeCount = buffer.getInt(NODE_COUNT_OFFSET);
        dataOffset = graphDataOffset(nodeCount);
        dataLength = buffer.getInt(graphDataLengthOffset(nodeCount));
        metatdataLength = graphMetadataLength(nodeCount, dataLength);
    }

    public UnsafeBuffer getBuffer()
    {
        return buffer;
    }

    public int nodeCount()
    {
        return nodeCount;
    }

    public int metadataLength()
    {
        return metatdataLength;
    }

    public int dataLength()
    {
        return dataLength;
    }

    public int nodeOffset(int nodeId)
    {
        return buffer.getInt(nodeIndexOffset(nodeId));
    }

    public int dataOffset()
    {
        return dataOffset;
    }

}
