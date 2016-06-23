package org.camunda.tngp.compactgraph;

import static uk.co.real_logic.agrona.BitUtil.*;

public class NodeDescriptor
{
    public static final int NODE_ID_OFFSET;

    public static final int NODE_DATA_POINTER_OFFSET;

    public static final int NODE_EDGE_POINTERS_OFFSET;

    public static final int NODE_BLOCK_LENGTH;

    public static final int NODE_DATA_HEADER_LENGTH = SIZE_OF_SHORT;

    static
    {
        int offset = 0;

        NODE_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        NODE_DATA_POINTER_OFFSET = offset;
        offset += SIZE_OF_INT;

        NODE_BLOCK_LENGTH = offset;

        NODE_EDGE_POINTERS_OFFSET = offset;
    }

    public static int nodeIdOffset(int offset)
    {
        return NODE_ID_OFFSET + offset;
    }

    public static int nodeDataPointerOffset(int offset)
    {
        return NODE_DATA_POINTER_OFFSET + offset;
    }

    public static int nodeEdgePointersOffset(int offset)
    {
        return NODE_EDGE_POINTERS_OFFSET + offset;
    }

    public static int nodeLength(int graphEdgeTypeCount, int nodeEdgeCount, int nodeDataLength)
    {
        int nodeLength = NODE_BLOCK_LENGTH;

        nodeLength += graphEdgeTypeCount * SIZE_OF_SHORT;
        nodeLength += nodeEdgeCount * SIZE_OF_INT;

        nodeLength += NODE_DATA_HEADER_LENGTH;
        nodeLength += nodeDataLength;

        nodeLength += align(nodeLength, 8) - nodeLength;

        return nodeLength;
    }

}
