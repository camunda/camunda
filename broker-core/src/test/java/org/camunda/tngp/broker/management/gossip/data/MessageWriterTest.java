package org.camunda.tngp.broker.management.gossip.data;


public class MessageWriterTest
{
//    protected final PeerList list = new PeerList();
//    protected final MessageWriter writer = new MessageWriter(list, ACK);
//
//    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
//    protected final GossipSyncRequestDecoder decoder = new GossipSyncRequestDecoder();
//
//    @Test
//    public void shouldWriteMessage()
//    {
//        // given
//        addPeer(list, createPeer("a", 8080, 555L, 111L, UP));
//        addPeer(list, createPeer("b", 9090, 777L, 100L, NULL_VAL));
//
//        final MutableDirectBuffer msg = new UnsafeBuffer(new byte[writer.getLength()]);
//
//        // when
//        writer.write(msg, 0);
//
//        // then
//        headerDecoder.wrap(msg, 0);
//        decoder.wrap(msg, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
//
//        PeersDecoder peersDecoder = decoder.peers();
//        assertThat(peersDecoder.hasNext()).isTrue();
//
//        peersDecoder = peersDecoder.next();
//        assertThat(peersDecoder.host()).isEqualTo("a");
//        assertThat(peersDecoder.port()).isEqualTo(8080);
//        assertThat(peersDecoder.generation()).isEqualTo(555L);
//        assertThat(peersDecoder.version()).isEqualTo(111L);
//        assertThat(peersDecoder.state()).isEqualTo(UP);
//
//        assertThat(peersDecoder.hasNext()).isTrue();
//
//        peersDecoder = peersDecoder.next();
//        assertThat(peersDecoder.host()).isEqualTo("b");
//        assertThat(peersDecoder.port()).isEqualTo(9090);
//        assertThat(peersDecoder.generation()).isEqualTo(777L);
//        assertThat(peersDecoder.version()).isEqualTo(100L);
//        assertThat(peersDecoder.state()).isEqualTo(NULL_VAL);
//
//        assertThat(peersDecoder.hasNext()).isFalse();
//    }
//
//    @Test
//    public void shouldReturnMessageLength()
//    {
//        // given
//        addPeer(list, createPeer("a", 8080, 555L, 111L, UP));
//        addPeer(list, createPeer("b", 9090, 777L, 100L, NULL_VAL));
//
//        // when
//        final int length = writer.getLength();
//
//        // then
//        assertThat(length).isEqualTo(56);
//    }
//
//    protected void addPeer(PeerList dst, Peer peer)
//    {
//        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[Peer.MAX_PEER_LENGTH]);
//        peer.write(buffer, 0);
//        dst.getPeers().add(buffer, 0, peer.getLength());
//    }
//
//    protected Peer createPeer(String hostname, int port, long generation, long version, PeerState state)
//    {
//        final Peer peer = new Peer();
//
//        peer.endpoint()
//            .port(port)
//            .host(hostname);
//
//        peer.heartbeat()
//            .generation(generation)
//            .version(version);
//
//        peer.state(state);
//
//        return peer;
//    }

}
