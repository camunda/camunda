package org.camunda.tngp.compactgraph;

import static org.agrona.BitUtil.*;

public class GraphMetadataDescriptor
{
    public static final int NODE_COUNT_OFFSET;

    public static final int NODE_INDEX_OFFSET;

    public static final int GRAPH_DATA_HEADER_LENGTH = SIZE_OF_SHORT;

    static
    {
        int offset = 0;
        NODE_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        NODE_INDEX_OFFSET = offset;
        offset += SIZE_OF_INT;
    }

    public static int nodeCountOffset(int offset)
    {
        return NODE_COUNT_OFFSET + 0;
    }

    public static int graphDataLengthOffset(int nodeCount)
    {
        return NODE_INDEX_OFFSET + (nodeCount * SIZE_OF_INT);
    }

    public static int graphDataOffset(int nodeCount)
    {
        return graphDataLengthOffset(nodeCount) + GRAPH_DATA_HEADER_LENGTH;
    }

    public static int graphMetadataLength(int nodeCount, int graphDataLength)
    {
        return align(graphDataOffset(nodeCount) + graphDataLength, 8);
    }

    public static int nodeIndexOffset(int nodeId)
    {
        return NODE_INDEX_OFFSET + (nodeId * SIZE_OF_INT);
    }

}
