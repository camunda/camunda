package org.camunda.tngp.broker.clustering.gossip.message;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.test.util.BufferWriterUtil.*;

import java.util.Iterator;

import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.junit.Test;


public class GossipMessageTest
{

    @Test
    public void testGossipRequest()
    {
        final PeerList expected = new PeerList(2);
        expected.append(
            new Peer()
                .alive()
        );
        expected.append(
            new Peer()
                .dead()
        );

        final GossipRequest gossipRequest = new GossipRequest()
            .peers(expected);

        final Iterator<Peer> actual = writeAndRead(gossipRequest).peers();

        assertThat(actual)
            .usingElementComparatorOnFields(
                "clientEndpoint",
                "managementEndpoint",
                "replicationEndpoint",
                "heartbeat",
                "state"
            )
            .hasSameElementsAs(expected);
    }

    @Test
    public void testGossipResponse()
    {
        final PeerList expected = new PeerList(2);
        expected.append(
            new Peer()
                .alive()
        );
        expected.append(
            new Peer()
                .dead()
        );

        final GossipResponse gossipResponse = new GossipResponse()
            .peers(expected);

        final Iterator<Peer> actual = writeAndRead(gossipResponse).peers();

        assertThat(actual)
            .usingElementComparatorOnFields(
                "clientEndpoint",
                "managementEndpoint",
                "replicationEndpoint",
                "heartbeat",
                "state"
            )
            .hasSameElementsAs(expected);
    }

    @Test
    public void testProbeRequest()
    {
        final ProbeRequest probeRequest = new ProbeRequest()
            .target(
                new Endpoint()
                    .host("test")
                    .port(111)
            );

        assertEqualFieldsAfterWriteAndRead(probeRequest,
            "target"
        );
    }

}
