package org.camunda.tngp.compactgraph;

import org.camunda.tngp.compactgraph.builder.GraphBuilder;
import org.camunda.tngp.compactgraph.builder.NodeBuilder;

import org.agrona.concurrent.UnsafeBuffer;

import static org.camunda.tngp.compactgraph.GraphMetadataDescriptor.*;
import static org.camunda.tngp.compactgraph.NodeDescriptor.*;
import static org.agrona.BitUtil.*;

import java.util.Iterator;
import java.util.List;

public class GraphEncoder
{
    protected GraphBuilder graphBuilder;

    protected int encodedLength;
    protected int metadataLength;
    protected int dataLength;

    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    public GraphEncoder(GraphBuilder builder)
    {
        this.graphBuilder = builder;
        this.metadataLength = calculateMetadataLength();
        this.dataLength = calculateDataLength();
        this.encodedLength = metadataLength + dataLength;

        buffer.wrap(new byte[encodedLength]);
    }

    public byte[] encode()
    {
        buffer.putInt(nodeCountOffset(0), graphBuilder.nodeCount());
        writeNodeIndex();
        writeNodes();
        writeGraphData();
        return buffer.byteArray();
    }

    protected void writeGraphData()
    {
        final byte[] graphData = graphBuilder.getGraphData();
        buffer.putShort(graphDataLengthOffset(graphBuilder.nodeCount()), (short) graphData.length);
        buffer.putBytes(graphDataOffset(graphBuilder.nodeCount()), graphData, 0, graphData.length);
    }

    protected void writeNodeIndex()
    {
        int writeOffset = metadataLength;

        final Iterator<NodeBuilder> nodeIterator = graphBuilder.nodeIterator();
        while (nodeIterator.hasNext())
        {
            final NodeBuilder nodeBuilder = nodeIterator.next();
            buffer.putInt(nodeIndexOffset(nodeBuilder.id()), writeOffset);
            writeOffset += nodeLength(graphBuilder.edgeTypeCount(), nodeBuilder.edgeCount(),
                    nodeBuilder.nodeDataLength());
        }
    }

    protected void writeNodes()
    {
        final Iterator<NodeBuilder> nodeIterator = graphBuilder.nodeIterator();
        while (nodeIterator.hasNext())
        {
            final NodeBuilder nodeBuilder = nodeIterator.next();
            writeNode(nodeBuilder, buffer.getInt(nodeIndexOffset(nodeBuilder.id())));
        }
    }

    protected void writeNode(NodeBuilder nodeBuilder, int nodeOffset)
    {
        buffer.putInt(nodeIdOffset(nodeOffset), nodeBuilder.id());

        final int edgeTypeCount = graphBuilder.edgeTypeCount();

        int edgeWriteIndex = nodeEdgePointersOffset(nodeOffset);
        for (short i = 0; i < edgeTypeCount; i++)
        {
            final List<NodeBuilder> edgesForType = nodeBuilder.edges(i);
            buffer.putShort(edgeWriteIndex, (short) edgesForType.size());
            edgeWriteIndex += SIZE_OF_SHORT;
            for (NodeBuilder connectedNode : edgesForType)
            {
                buffer.putInt(edgeWriteIndex, buffer.getInt(nodeIndexOffset(connectedNode.id())));
                edgeWriteIndex += SIZE_OF_INT;
            }
        }

        final int nodeDataOffset = edgeWriteIndex;

        buffer.putInt(nodeDataPointerOffset(nodeOffset), nodeDataOffset);
        buffer.putShort(nodeDataOffset, (short) nodeBuilder.nodeDataLength());
        buffer.putBytes(nodeDataOffset + NODE_DATA_HEADER_LENGTH, nodeBuilder.nodeData());
    }

    public int encodedLength()
    {
        return encodedLength;
    }

    public int dataLength()
    {
        return dataLength;
    }

    public int metadataLength()
    {
        return metadataLength;
    }

    private int calculateMetadataLength()
    {
        return graphMetadataLength(graphBuilder.nodeCount(), graphBuilder.graphDataLength());
    }

    private int calculateDataLength()
    {
        int size = 0;

        final Iterator<NodeBuilder> nodeIterator = graphBuilder.nodeIterator();
        while (nodeIterator.hasNext())
        {
            final NodeBuilder nodeBuilder = nodeIterator.next();
            size += nodeLength(graphBuilder.edgeTypeCount(), nodeBuilder.edgeCount(), nodeBuilder.nodeDataLength());
        }

        return size;
    }

}
