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
