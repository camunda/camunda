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

import static org.agrona.BitUtil.*;

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
