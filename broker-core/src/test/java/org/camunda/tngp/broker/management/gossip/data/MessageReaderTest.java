package org.camunda.tngp.broker.management.gossip.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.management.gossip.PeerState.ALIVE;
import static org.camunda.tngp.management.gossip.PeerState.NULL_VAL;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.message.GossipReader;
import org.camunda.tngp.management.gossip.GossipEncoder;
import org.camunda.tngp.management.gossip.MessageHeaderEncoder;
import org.junit.Test;

public class MessageReaderTest
{
    protected final MutableDirectBuffer msg = new UnsafeBuffer(new byte[256]);
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final GossipEncoder bodyEncoder = new GossipEncoder();

    protected final GossipReader reader = new GossipReader();

    @Test
    public void shouldReturnNextPeer()
    {
        // given
        headerEncoder.wrap(msg, 0)
            .blockLength(bodyEncoder.sbeBlockLength());

        bodyEncoder.wrap(msg, headerEncoder.encodedLength())
            .peersCount(2)
            .next()
                .host("a")
                .port(8080)
                .generation(555L)
                .version(111L)
                .state(ALIVE)
            .next()
                .host("b")
                .port(8080)
                .generation(777L)
                .version(999L)
                .state(NULL_VAL);

        // when
        reader.wrap(msg, 0, msg.capacity());

        // then
        Peer peer = reader.next();
        assertThat(peer.endpoint()).isNotNull();
        assertThat(peer.endpoint().host()).isEqualTo("a");
        assertThat(peer.endpoint().port()).isEqualTo(8080);
        assertThat(peer.heartbeat()).isNotNull();
        assertThat(peer.heartbeat().generation()).isEqualTo(555L);
        assertThat(peer.heartbeat().version()).isEqualTo(111L);
        assertThat(peer.state()).isEqualTo(ALIVE);

        peer = reader.next();
        assertThat(peer.endpoint()).isNotNull();
        assertThat(peer.endpoint().host()).isEqualTo("b");
        assertThat(peer.endpoint().port()).isEqualTo(8080);
        assertThat(peer.heartbeat()).isNotNull();
        assertThat(peer.heartbeat().generation()).isEqualTo(777L);
        assertThat(peer.heartbeat().version()).isEqualTo(999L);
        assertThat(peer.state()).isEqualTo(NULL_VAL);
    }

    @Test
    public void shouldNotHaveNext()
    {
        // given
        headerEncoder.wrap(msg, 0)
            .blockLength(bodyEncoder.sbeBlockLength());

        bodyEncoder.wrap(msg, headerEncoder.encodedLength())
            .peersCount(0);

        reader.wrap(msg, 0, msg.capacity());

        // when + then
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldHaveNext()
    {
        // given
        headerEncoder.wrap(msg, 0)
            .blockLength(bodyEncoder.sbeBlockLength());

        bodyEncoder.wrap(msg, headerEncoder.encodedLength())
            .peersCount(1)
            .next()
                .host("a")
                .port(8080)
                .generation(555L)
                .version(111L)
                .state(ALIVE);

        reader.wrap(msg, 0, msg.capacity());

        // when + then
        assertThat(reader.hasNext()).isTrue();
    }

}
