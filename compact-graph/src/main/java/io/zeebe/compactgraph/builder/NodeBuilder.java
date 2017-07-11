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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class NodeBuilder
{
    protected final GraphBuilder graphBuilder;
    protected final int id;
    protected byte[] nodeData = new byte[0];

    protected int edgeCount = 0;

    protected final TreeMap<Integer, List<NodeBuilder>> edges = new TreeMap<>(Integer::compareTo);

    public NodeBuilder(int id, GraphBuilder graphBuilder)
    {
        this.id = id;
        this.graphBuilder = graphBuilder;
    }

    public int id()
    {
        return id;
    }

    public NodeBuilder nodeData(byte[] data)
    {
        if (data.length > Short.MAX_VALUE)
        {
            throw new IllegalArgumentException(String.format("Node data cannot be larger than %s", +Short.MAX_VALUE));
        }
        this.nodeData = data;
        return this;
    }

    public int nodeDataLength()
    {
        return nodeData.length;
    }

    public byte[] nodeData()
    {
        return nodeData;
    }

    public GraphBuilder done()
    {
        return graphBuilder;
    }

    public NodeBuilder connectTo(final int nodeId, final int edgeType)
    {
        if (edgeType < 0 || edgeType >= graphBuilder.edgeTypeCount())
        {
            throw new IllegalArgumentException(String.format("Edge type %s does not exist.", edgeType));
        }

        List<NodeBuilder> edgesForType = edges.get(edgeType);

        if (edgesForType == null)
        {
            edgesForType = new ArrayList<>();
            edges.put(edgeType, edgesForType);
        }

        edgesForType.add(graphBuilder.node(nodeId));
        ++edgeCount;

        return this;
    }

    public NodeBuilder connect(final int nodeId, final int outgoingEdgeType, final int incommingEdgeType)
    {
        connectTo(nodeId, outgoingEdgeType)
            .done()
            .node(nodeId)
            .connectTo(id, incommingEdgeType);

        return this;
    }

    public List<NodeBuilder> edges(int edgeType)
    {
        List<NodeBuilder> edgesByType = edges.get(edgeType);
        if (edgesByType == null)
        {
            edgesByType = Collections.emptyList();
        }
        return edgesByType;
    }

    public int edgeCount()
    {
        return edgeCount;
    }

}
