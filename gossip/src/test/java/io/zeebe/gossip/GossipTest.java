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
package io.zeebe.gossip;

import java.util.Arrays;

import io.zeebe.transport.SocketAddress;
import org.junit.Rule;
import org.junit.Test;

public class GossipTest
{
    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

    private ActorSchedulerRule actorScheduler = new ActorSchedulerRule();

    private GossipRule gossip1 = new GossipRule(actorScheduler, CONFIGURATION, "localhost", 8001);
    private GossipRule gossip2 = new GossipRule(actorScheduler, CONFIGURATION, "localhost", 8002);
    private GossipRule gossip3 = new GossipRule(actorScheduler, CONFIGURATION, "localhost", 8003);

    @Rule
    public GossipClusterRule clusterRule = new GossipClusterRule(actorScheduler, gossip1, gossip2, gossip3);

    @Test
    public void shouldJoinWithSingleContactPoint() throws Exception
    {
        gossip2.join("localhost", 8001);
        gossip2.awaitAdded("localhost", 8001);

        gossip3.join("localhost", 8001);
        gossip3.awaitAdded("localhost", 8001);
        gossip3.awaitAdded("localhost", 8002);

        gossip1.awaitAdded("localhost", 8002);
        gossip1.awaitAdded("localhost", 8003);
        gossip2.awaitAdded("localhost", 8003);
    }

    @Test
    public void shouldJoinWithMultipleContactPoints() throws Exception
    {
        gossip2.join("localhost", 8001);
        gossip2.awaitAdded("localhost", 8001);

        gossip3.getController()
            .join(Arrays.asList(
                                new SocketAddress("localhost", 8001),
                                new SocketAddress("localhost", 8002)));

        gossip3.awaitAdded("localhost", 8001);
        gossip3.awaitAdded("localhost", 8002);

        gossip1.awaitAdded("localhost", 8002);
        gossip1.awaitAdded("localhost", 8003);
    }

    @Test
    public void shouldJoinIfOneContactPointIsNotAvailable() throws Exception
    {
        gossip2.join("localhost", 8001);
        gossip2.awaitAdded("localhost", 8001);

        gossip3.interruptConnection("localhost", 8001);

        gossip3.getController()
            .join(Arrays.asList(
                                new SocketAddress("localhost", 8001),
                                new SocketAddress("localhost", 8002)));

        gossip3.awaitAdded("localhost", 8003);
        gossip3.awaitAdded("localhost", 8002);
    }

    @Test
    public void shouldTryJoinUntilOneContactPointIsAvailable() throws Exception
    {
        gossip2.interruptConnection("localhost", 8001);

        gossip2.join("localhost", 8001);

        Thread.sleep(2_000);

        gossip2.reconnect("localhost", 8001);

        Thread.sleep(CONFIGURATION.getJoinInterval());

        gossip2.awaitAdded("localhost", 8001);
    }

    @Test
    public void shouldRemoveSuspiciousMember() throws Exception
    {
        gossip2.join("localhost", 8001);
        gossip3.join("localhost", 8001);

        gossip1.awaitAdded("localhost", 8002);
        gossip1.awaitAdded("localhost", 8003);

        gossip1.interruptConnection("localhost", 8002);
        gossip3.interruptConnection("localhost", 8002);
        gossip2.interruptConnection("localhost", 8001);
        gossip2.interruptConnection("localhost", 8003);

        final long suspicionTimeout = GossipMathUtil.suspicionTimeout(CONFIGURATION.getSuspicionMultiplier(), 3, CONFIGURATION.getProbeInterval());
        Thread.sleep(suspicionTimeout);

        Thread.sleep(4_000);

        gossip1.awaitRemoved("localhost", 8002);
        gossip3.awaitRemoved("localhost", 8002);

        Thread.sleep(4_000);
    }

    @Test
    public void testPing() throws Exception
    {
        gossip2.join("localhost", 8001);

        Thread.sleep(4_000);
    }

    @Test
    public void testPingReq() throws Exception
    {
        gossip2.join("localhost", 8001);
        gossip3.join("localhost", 8001);

        gossip1.awaitAdded("localhost", 8002);
        gossip1.awaitAdded("localhost", 8003);

        gossip2.interruptConnection("localhost", 8001);

        Thread.sleep(4_000);
    }

}
