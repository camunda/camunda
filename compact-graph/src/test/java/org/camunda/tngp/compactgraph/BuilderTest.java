package org.camunda.tngp.compactgraph;

import org.camunda.tngp.compactgraph.builder.GraphBuilder;
import org.camunda.tngp.compactgraph.builder.NodeBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class BuilderTest
{
    GraphBuilder graphBuilder;

    @Before
    public void setup()
    {
        graphBuilder = new GraphBuilder();
    }


    @Test
    public void shouldGenerateNodeIdsInOrder()
    {
        assertThat(graphBuilder.newNode().id()).isEqualTo(0);
        assertThat(graphBuilder.newNode().id()).isEqualTo(1);
        assertThat(graphBuilder.newNode().id()).isEqualTo(2);
        assertThat(graphBuilder.newNode().id()).isEqualTo(3);
    }

    @Test
    public void shouldGetNodeBuilder()
    {
        assertThat(graphBuilder.newNode()).isSameAs(graphBuilder.node(0));
        assertThat(graphBuilder.newNode()).isNotSameAs(graphBuilder.node(0));
    }

    @Test
    public void shouldSetGraphBuilderOnNodeBuilder()
    {
        assertThat(graphBuilder.newNode().done()).isSameAs(graphBuilder);
    }

    @Test
    public void shouldSetNodeDataLengthOnNodeBuilder()
    {
        NodeBuilder node = graphBuilder.newNode();
        node.nodeData(new byte[64]);
        assertThat(node.nodeDataLength()).isSameAs(64);
    }

    @Test
    public void shouldConnect()
    {
        final short edgeType = 0;
        graphBuilder.edgeTypeCount((short) 1);
        final NodeBuilder node1 = graphBuilder.newNode().done().newNode();
        node1.connectTo(0, edgeType);
    }

    @Test
    public void shouldConnectToSelf()
    {
        final short edgeType = 0;
        graphBuilder.edgeTypeCount((short) 1);
        final NodeBuilder node0 = graphBuilder.newNode();
        node0.connectTo(0, edgeType);
    }


}
