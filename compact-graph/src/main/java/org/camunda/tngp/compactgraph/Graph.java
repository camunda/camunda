package org.camunda.tngp.compactgraph;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.camunda.tngp.compactgraph.GraphMetadataDescriptor.*;

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
