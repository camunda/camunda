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
package io.zeebe.compactgraph.builder;

import java.util.Iterator;
import java.util.TreeMap;

public class GraphBuilder
{
    protected int nextNodeId = 0;

    protected final TreeMap<Integer, NodeBuilder> nodeBuilders = new TreeMap<>(Integer::compareTo);
    protected short edgeTypeCount = 0;
    protected byte[] graphData = new byte[0];

    public NodeBuilder newNode()
    {
        final int nodeId = nextNodeId++;
        final NodeBuilder builder = new NodeBuilder(nodeId, this);
        nodeBuilders.put(nodeId, builder);
        return builder;
    }

    public NodeBuilder node(int id)
    {
        final NodeBuilder nodeBuilder = nodeBuilders.get(id);

        if (nodeBuilder == null)
        {
            throw new IllegalArgumentException(String.format("Node with id %s is not defined", id));
        }

        return nodeBuilder;
    }

    public GraphBuilder edgeTypeCount(int edgeTyes)
    {
        if (edgeTyes < 0 || edgeTyes > Short.MAX_VALUE)
        {
            throw new IllegalArgumentException("Edge type count must be withing the positive short range");
        }
        this.edgeTypeCount = (short) edgeTyes;
        return this;
    }

    public GraphBuilder graphData(byte[] data)
    {
        this.graphData = data;
        return this;
    }

    public int graphDataLength()
    {
        return graphData.length;
    }

    public byte[] getGraphData()
    {
        return graphData;
    }

    public Iterator<NodeBuilder> nodeIterator()
    {
        return nodeBuilders.values().iterator();
    }

    public int nodeCount()
    {
        return nextNodeId;
    }

    public int edgeTypeCount()
    {
        return edgeTypeCount;
    }
}
