package org.camunda.tngp.compactgraph;

import static org.camunda.tngp.compactgraph.NodeDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class GraphNavigator
{
    protected UnsafeBuffer buffer;
    protected int offset = -1;
    protected Graph graph;

    public GraphNavigator init(Graph graph)
    {
        this.graph = graph;
        this.buffer = graph.getBuffer();
        return this;
    }

    public GraphNavigator moveToNode(int nodeId)
    {
        setOffset(graph.nodeOffset(nodeId));
        return this;
    }

    protected void setOffset(int nodeOffset)
    {
        this.offset = nodeOffset;
    }

    public int getNodeId()
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

        for(short i = 0; i < edgeType; i++)
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

    public GraphNavigator traverseEdge(int edgeType)
    {
        return traverseEdge(edgeType, 0);
    }

    public GraphNavigator traverseEdge(int edgeType, int edgeIndex)
    {
        final int edgeCountOffset = edgeCountOffset((short) edgeType);
        final short edgeCount = buffer.getShort(edgeCountOffset);
        if(edgeIndex < 0 || edgeIndex >= edgeCount)
        {
            throw new IllegalArgumentException("No edge with type "+edgeType +" and index "+edgeIndex);
        }
        setOffset(buffer.getInt(edgeCountOffset + SIZE_OF_SHORT + (edgeIndex * SIZE_OF_INT)));

        return this;
    }

}
