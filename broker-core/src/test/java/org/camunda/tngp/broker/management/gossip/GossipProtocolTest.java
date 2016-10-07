package org.camunda.tngp.broker.management.gossip;

public class GossipProtocolTest
{
//    protected final ManagementComponentCfg cfg = new ManagementComponentCfg();
//    protected final GossipProtocol gossipProtocol = new GossipProtocol(cfg);
//
//    @Before
//    public void setup()
//    {
//        cfg.host = "127.0.0.1";
//        cfg.port = 51016;
//    }
//
//    @Test
//    public void shouldAddLocalPeerToListOfMembers()
//    {
//        // when
//        gossipProtocol.start();
//
//        // then
//        assertThat(gossipProtocol.getMembers().size()).isEqualTo(1);
//    }
//
//    @Test
//    public void shouldInitializeLocalPeer()
//    {
//        // when
//        gossipProtocol.start();
//
//        // then
//        final Peer peer = new Peer();
//        gossipProtocol.getMembers().get(0, peer);
//        assertThat(peer.endpoint().host()).isEqualTo("127.0.0.1");
//        assertThat(peer.endpoint().port()).isEqualTo(51016);
//        assertThat(peer.heartbeat().generation()).isGreaterThan(0L);
//        assertThat(peer.heartbeat().version()).isEqualTo(0L);
//        assertThat(peer.state()).isEqualTo(UP);
//    }
//
//    @Test
//    public void shouldAddInitialContactPoint()
//    {
//        // given
//        final String[] initialContactPoints = { "127.0.0.1:9090" };
//        cfg.gossip.initialContactPoints = initialContactPoints;
//
//        // when
//        gossipProtocol.start();
//
//        // then
//        assertThat(gossipProtocol.getMembers().size()).isEqualTo(2);
//    }
//
//    @Test
//    public void shouldInitializeContactPoint()
//    {
//        // given
//        final String[] initialContactPoints = { "127.0.0.1:9090" };
//        cfg.gossip.initialContactPoints = initialContactPoints;
//
//        // when
//        gossipProtocol.start();
//
//        // then
//        final Peer peer = new Peer();
//        gossipProtocol.getMembers().get(0, peer);
//        assertThat(peer.endpoint().host()).isEqualTo("127.0.0.1");
//        assertThat(peer.endpoint().port()).isEqualTo(9090);
//        assertThat(peer.heartbeat().generation()).isEqualTo(0L);
//        assertThat(peer.heartbeat().version()).isEqualTo(0L);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//    }
//
//    @Test
//    public void shouldInitGossiper()
//    {
//        // when
//        gossipProtocol.start();
//
//        // then
//        assertThat(gossipProtocol.getGossipers().length).isEqualTo(1);
//    }
}
