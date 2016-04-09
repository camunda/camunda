package org.camunda.tngp.compactgraph;

import org.camunda.tngp.compactgraph.builder.GraphBuilder;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

public class EncoderTest
{

    GraphBuilder graphBuilder;
    Graph graph;
    GraphNavigator graphNavigator;

    @Before
    public void setup()
    {
        graphBuilder = new GraphBuilder();
        graph = new Graph();
        graphNavigator = new GraphNavigator();
    }

    @Test
    public void shouldEncodeEmptyGraph()
    {
        GraphEncoder encoder = new GraphEncoder(graphBuilder);
        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(0);
    }

    @Test
    public void shouldEncodeSingleNode()
    {
        graphBuilder.newNode();
        GraphEncoder encoder = new GraphEncoder(graphBuilder);

        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(1);
        assertThat(graph.nodeOffset(0)).isEqualTo(graph.metadataLength());

        graphNavigator.init(graph)
            .moveToNode(0);

        assertThat(graphNavigator.getNodeId()).isEqualTo(0);
    }

    @Test
    public void shouldEncodeConnectedNodesNode()
    {
        graphBuilder
            .edgeTypeCount(1)
            .newNode().done()
            .newNode().connect(0, 0, 0);

        GraphEncoder encoder = new GraphEncoder(graphBuilder);

        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(2);
        assertThat(graph.nodeOffset(0)).isEqualTo(graph.metadataLength());

        graphNavigator.init(graph).moveToNode(0);

        // traverse edge
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(1);
        assertThat(graphNavigator.traverseEdge(0).getNodeId()).isEqualTo(1);
        // traverse back
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(1);
        assertThat(graphNavigator.traverseEdge(0).getNodeId()).isEqualTo(0);

    }

    @Test
    public void shouldEncodeMultipleEdeTypes()
    {
        graphBuilder
            .edgeTypeCount(2)
            .newNode().done()
            .newNode().connect(0, 1, 0);

        GraphEncoder encoder = new GraphEncoder(graphBuilder);

        graph.wrap(encoder.encode());
        assertThat(graph.nodeCount()).isEqualTo(2);
        assertThat(graph.nodeOffset(0)).isEqualTo(graph.metadataLength());

        graphNavigator.init(graph).moveToNode(0);

        // traverse edge
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(1);
        assertThat(graphNavigator.edgeCount(1)).isEqualTo(0);
        assertThat(graphNavigator.traverseEdge(0).getNodeId()).isEqualTo(1);

        // traverse back
        assertThat(graphNavigator.edgeCount(0)).isEqualTo(0);
        assertThat(graphNavigator.edgeCount(1)).isEqualTo(1);
        assertThat(graphNavigator.traverseEdge(1).getNodeId()).isEqualTo(0);

    }
}
