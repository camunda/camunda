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

import static io.zeebe.compactgraph.NodeDescriptor.*;
import static org.agrona.BitUtil.*;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class NodeVisitor
{
    protected UnsafeBuffer buffer;
    protected int offset = -1;
    protected Graph graph;

    public NodeVisitor init(Graph graph)
    {
        this.graph = graph;
        this.buffer = graph.getBuffer();
        return this;
    }

    public NodeVisitor moveToNode(int nodeId)
    {
        setOffset(graph.nodeOffset(nodeId));
        return this;
    }

    protected void setOffset(int nodeOffset)
    {
        this.offset = nodeOffset;
    }

    public int nodeId()
    {
        return buffer.getInt(nodeIdOffset(offset));
    }

    public int nodeDataOffset()
    {
        return buffer.getInt(nodeDataPointerOffset(offset)) + NODE_DATA_HEADER_LENGTH;
    }

    public int nodeDataLength()
    {
        return buffer.getShort(buffer.getShort(nodeDataPointerOffset(offset)));
    }

    public DirectBuffer getBuffer()
    {
        return buffer;
    }

    protected int edgeCountOffset(short edgeType)
    {
        int offset = nodeEdgePointersOffset(this.offset);

        for (short i = 0; i < edgeType; i++)
        {
            final short pointersCountByType = buffer.getShort(offset);
            offset += SIZE_OF_SHORT;
            offset += pointersCountByType * SIZE_OF_INT;
        }

        return offset;
    }

    public int edgeCount(int edgeType)
    {
        return buffer.getShort(edgeCountOffset((short) edgeType));
    }

    public NodeVisitor traverseEdge(int edgeType)
    {
        return traverseEdge(edgeType, 0);
    }

    public NodeVisitor traverseEdge(int edgeType, int edgeIndex)
    {
        final int edgeCountOffset = edgeCountOffset((short) edgeType);
        final short edgeCount = buffer.getShort(edgeCountOffset);
        if (edgeIndex < 0 || edgeIndex >= edgeCount)
        {
            final String exceptionMessage = String.format("No edge with type %s and index %s" + edgeIndex, edgeType, edgeIndex);
            throw new IllegalArgumentException(exceptionMessage);
        }
        setOffset(buffer.getInt(edgeCountOffset + SIZE_OF_SHORT + (edgeIndex * SIZE_OF_INT)));

        return this;
    }

}
