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
import io.zeebe.compactgraph.builder.NodeBuilder;
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
        final NodeBuilder node = graphBuilder.newNode();
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
