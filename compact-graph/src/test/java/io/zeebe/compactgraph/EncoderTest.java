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

import io.zeebe.compactgraph.builder.GraphBuilder;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

public class EncoderTest
{

    GraphBuilder graphBuilder;
    Graph graph;
    NodeVisitor graphNavigator;

    @Before
    public void setup()
    {
        graphBuilder = new GraphBuilder();
        graph = new Graph();
        graphNavigator = new NodeVisitor();
    }

    @Test
    public void shouldEncodeEmptyGraph()
    {
        final GraphEncoder encoder = new GraphEncoder(graphBuilder);
        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(0);
    }

    @Test
    public void shouldEncodeSingleNode()
    {
        graphBuilder.newNode();
        final GraphEncoder encoder = new GraphEncoder(graphBuilder);

        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(1);
        assertThat(graph.nodeOffset(0)).isEqualTo(graph.metadataLength());

        graphNavigator.init(graph)
            .moveToNode(0);

        assertThat(graphNavigator.nodeId()).isEqualTo(0);
    }

    @Test
    public void shouldEncodeConnectedNodesNode()
    {
        graphBuilder
            .edgeTypeCount(1)
            .newNode().done()
            .newNode().connect(0, 0, 0);

        final GraphEncoder encoder = new GraphEncoder(graphBuilder);

        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(2);
        assertThat(graph.nodeOffset(0)).isEqualTo(graph.metadataLength());

        graphNavigator.init(graph).moveToNode(0);

        // traverse edge
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(1);
        assertThat(graphNavigator.traverseEdge(0).nodeId()).isEqualTo(1);
        // traverse back
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(1);
        assertThat(graphNavigator.traverseEdge(0).nodeId()).isEqualTo(0);

    }

    @Test
    public void shouldEncodeMultipleEdeTypes()
    {
        graphBuilder
            .edgeTypeCount(2)
            .newNode().done()
            .newNode().connect(0, 1, 0);

        final GraphEncoder encoder = new GraphEncoder(graphBuilder);

        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(2);
        assertThat(graph.nodeOffset(0)).isEqualTo(graph.metadataLength());

        graphNavigator.init(graph).moveToNode(0);

        // traverse edge
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(1);
        assertThat(graphNavigator.edgeCount(1)).isEqualTo(0);
        assertThat(graphNavigator.traverseEdge(0).nodeId()).isEqualTo(1);

        // traverse back
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(0);
        assertThat(graphNavigator.edgeCount(1)).isEqualTo(1);
        assertThat(graphNavigator.traverseEdge(1).nodeId()).isEqualTo(0);

    }
}
