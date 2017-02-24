package org.camunda.tngp.broker.management.gossip.data;

public class PeerTest
{
//    @Test
//    public void shouldDecodePeer()
//    {
//        // given
//        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[Peer.MAX_PEER_LENGTH]);
//        final PeerDescriptorEncoder encoder = new PeerDescriptorEncoder();
//        encoder.wrap(buffer, 0)
//            .host("localhost")
//            .port(8080)
//            .generation(777L)
//            .version(555L)
//            .state(ALIVE);
//
//        final Peer peer = new Peer();
//
//        // when
//        peer.wrap(buffer, 0, buffer.capacity());
//
//        // then
//        assertThat(peer.clientEndpoint()).isNotNull();
//        assertThat(peer.clientEndpoint().port()).isEqualTo(8080);
//        assertThat(peer.clientEndpoint().host()).isEqualTo("localhost");
//
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(777L);
//        assertThat(peer.heartbeat().version()).isEqualTo(555L);
//
//        assertThat(peer.state()).isEqualTo(ALIVE);
//    }
//
//    @Test
//    public void shouldEncodePeer()
//    {
//        // given
//        final Peer peer = new Peer();
//
//        peer.clientEndpoint()
//            .port(8080)
//            .host("localhost");
//
//        peer.heartbeat()
//            .generation(777L)
//            .version(555L);
//
//        peer.state(ALIVE);
//
//        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[Peer.MAX_PEER_LENGTH]);
//        peer.write(buffer, 0);
//
//        final PeerDescriptorDecoder decoder = new PeerDescriptorDecoder();
//
//        // when
//        decoder.wrap(buffer, 0, BLOCK_LENGTH, SCHEMA_VERSION);
//
//        // then
//        assertThat(decoder.port()).isEqualTo(8080);
//        assertThat(decoder.host()).isEqualTo("localhost");
//        assertThat(decoder.generation()).isEqualTo(777L);
//        assertThat(decoder.version()).isEqualTo(555L);
//        assertThat(decoder.state()).isEqualTo(ALIVE);
//    }
//
//    @Test
//    public void shouldReturnThisPeerIsLessByHostname()
//    {
//        // given
//        final Peer thisPeer = new Peer();
//        thisPeer.clientEndpoint().host("a");
//
//        final Peer thatPeer = new Peer();
//        thatPeer.clientEndpoint().host("z");
//
//        // when
//        final int cmp = thisPeer.compareTo(thatPeer);
//
//        // then
//        assertThat(cmp).isLessThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisPeerIsEqualByHostname()
//    {
//        // given
//        final Peer thisPeer = new Peer();
//        thisPeer.clientEndpoint().host("a");
//
//        final Peer thatPeer = new Peer();
//        thatPeer.clientEndpoint().host("a");
//
//        // when
//        final int cmp = thisPeer.compareTo(thatPeer);
//
//        // then
//        assertThat(cmp).isEqualTo(0);
//    }
//
//    @Test
//    public void shouldReturnThisPeerIsGreaterByHostname()
//    {
//        // given
//        final Peer thisPeer = new Peer();
//        thisPeer.clientEndpoint().host("z");
//
//        final Peer thatPeer = new Peer();
//        thatPeer.clientEndpoint().host("a");
//
//        // when
//        final int cmp = thisPeer.compareTo(thatPeer);
//
//        // then
//        assertThat(cmp).isGreaterThan(0);
//    }
//
//    @Test
//    @Ignore
//    public void shouldReturnThisPeerIsLessByPort()
//    {
//        // given
//        final Peer thisPeer = new Peer();
//        thisPeer.clientEndpoint().host("a");
//        thisPeer.clientEndpoint().port(8080);
//
//        final Peer thatPeer = new Peer();
//        thatPeer.clientEndpoint().host("a");
//        thatPeer.clientEndpoint().port(9090);
//
//        // when
//        final int cmp = thisPeer.compareTo(thatPeer);
//
//        // then
//        assertThat(cmp).isLessThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisPeerIsEqualByPort()
//    {
//        // given
//        final Peer thisPeer = new Peer();
//        thisPeer.clientEndpoint().host("a");
//        thisPeer.clientEndpoint().port(8080);
//
//        final Peer thatPeer = new Peer();
//        thatPeer.clientEndpoint().host("a");
//        thatPeer.clientEndpoint().port(8080);
//
//        // when
//        final int cmp = thisPeer.compareTo(thatPeer);
//
//        // then
//        assertThat(cmp).isEqualTo(0);
//    }
//
//    @Test
//    @Ignore
//    public void shouldReturnThisPeerIsGreaterByPort()
//    {
//        // given
//        final Peer thisPeer = new Peer();
//        thisPeer.clientEndpoint().host("a");
//        thisPeer.clientEndpoint().port(9090);
//
//        final Peer thatPeer = new Peer();
//        thatPeer.clientEndpoint().host("a");
//        thatPeer.clientEndpoint().port(8080);
//
//        // when
//        final int cmp = thisPeer.compareTo(thatPeer);
//
//        // then
//        assertThat(cmp).isGreaterThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisEndpointIsLessByHostname()
//    {
//        // given
//        final Endpoint thisEndpoint = new Endpoint();
//        thisEndpoint.host("a");
//
//        final Endpoint thatEndpoint = new Endpoint();
//        thatEndpoint.host("z");
//
//        // when
//        final int cmp = thisEndpoint.compareTo(thatEndpoint);
//
//        // then
//        assertThat(cmp).isLessThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisEndpointIsEqualByHostname()
//    {
//        // given
//        final Endpoint thisEndpoint = new Endpoint();
//        thisEndpoint.host("a");
//
//        final Endpoint thatEndpoint = new Endpoint();
//        thatEndpoint.host("a");
//
//        // when
//        final int cmp = thisEndpoint.compareTo(thatEndpoint);
//
//        // then
//        assertThat(cmp).isEqualTo(0);
//    }
//
//    @Test
//    public void shouldReturnThisEndpointIsGreaterByHostname()
//    {
//        // given
//        final Endpoint thisEndpoint = new Endpoint();
//        thisEndpoint.host("z");
//
//        final Endpoint thatEndpoint = new Endpoint();
//        thatEndpoint.host("a");
//
//        // when
//        final int cmp = thisEndpoint.compareTo(thatEndpoint);
//
//        // then
//        assertThat(cmp).isGreaterThan(0);
//    }
//
//    @Test
//    @Ignore
//    public void shouldReturnThisEndpointIsLessByPort()
//    {
//        // given
//        final Endpoint thisEndpoint = new Endpoint();
//        thisEndpoint.host("a").port(8080);
//
//        final Endpoint thatEndpoint = new Endpoint();
//        thatEndpoint.host("a").port(9090);
//
//        // when
//        final int cmp = thisEndpoint.compareTo(thatEndpoint);
//
//        // then
//        assertThat(cmp).isLessThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisEndpointIsEqualByPort()
//    {
//        // given
//        final Endpoint thisEndpoint = new Endpoint();
//        thisEndpoint.host("a").port(8080);
//
//        final Endpoint thatEndpoint = new Endpoint();
//        thatEndpoint.host("a").port(8080);
//
//        // when
//        final int cmp = thisEndpoint.compareTo(thatEndpoint);
//
//        // then
//        assertThat(cmp).isEqualTo(0);
//    }
//
//    @Test
//    @Ignore
//    public void shouldReturnThisEndpointIsGreaterByPort()
//    {
//        // given
//        final Endpoint thisEndpoint = new Endpoint();
//        thisEndpoint.host("a").port(9090);
//
//        final Endpoint thatEndpoint = new Endpoint();
//        thatEndpoint.host("a").port(8080);
//
//        // when
//        final int cmp = thisEndpoint.compareTo(thatEndpoint);
//
//        // then
//        assertThat(cmp).isGreaterThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisHeartbeatIsLessByGeneration()
//    {
//        // given
//        final Heartbeat thisHeartbeat = new Heartbeat();
//        thisHeartbeat.generation(555L);
//
//        final Heartbeat thatHeartbeat = new Heartbeat();
//        thatHeartbeat.generation(777L);
//
//        // when
//        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);
//
//        // then
//        assertThat(cmp).isLessThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisHeartbeatIsEqualByGeneration()
//    {
//        // given
//        final Heartbeat thisHeartbeat = new Heartbeat();
//        thisHeartbeat.generation(555L);
//
//        final Heartbeat thatHeartbeat = new Heartbeat();
//        thatHeartbeat.generation(555L);
//
//        // when
//        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);
//
//        // then
//        assertThat(cmp).isEqualTo(0);
//    }
//
//    @Test
//    public void shouldReturnThisHeartbeatIsGreaterByGeneration()
//    {
//        // given
//        final Heartbeat thisHeartbeat = new Heartbeat();
//        thisHeartbeat.generation(777L);
//
//        final Heartbeat thatHeartbeat = new Heartbeat();
//        thatHeartbeat.generation(555L);
//
//        // when
//        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);
//
//        // then
//        assertThat(cmp).isGreaterThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisHeartbeatIsLessByVersion()
//    {
//        // given
//        final Heartbeat thisHeartbeat = new Heartbeat();
//        thisHeartbeat.generation(555L).version(10);
//
//        final Heartbeat thatHeartbeat = new Heartbeat();
//        thatHeartbeat.generation(555L).version(100);
//
//        // when
//        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);
//
//        // then
//        assertThat(cmp).isLessThan(0);
//    }
//
//    @Test
//    public void shouldReturnThisHeartbeatIsEqualsByVersion()
//    {
//        // given
//        final Heartbeat thisHeartbeat = new Heartbeat();
//        thisHeartbeat.generation(555L).version(100);
//
//        final Heartbeat thatHeartbeat = new Heartbeat();
//        thatHeartbeat.generation(555L).version(100);
//
//        // when
//        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);
//
//        // then
//        assertThat(cmp).isEqualTo(0);
//    }
//
//    @Test
//    public void shouldReturnThisHeartbeatIsEqualByVersion()
//    {
//        // given
//        final Heartbeat thisHeartbeat = new Heartbeat();
//        thisHeartbeat.generation(555L).version(100);
//
//        final Heartbeat thatHeartbeat = new Heartbeat();
//        thatHeartbeat.generation(555L).version(10);
//
//        // when
//        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);
//
//        // then
//        assertThat(cmp).isGreaterThan(0);
//    }

}
