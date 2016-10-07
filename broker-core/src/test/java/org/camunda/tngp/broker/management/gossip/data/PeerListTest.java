package org.camunda.tngp.broker.management.gossip.data;


public class PeerListTest
{
//    protected static final int DEFAULT_PORT = 8080;
//    protected static final PeerState DEFAULT_STATE = UP;
//    protected static final long DEFAULT_GENERATION = 555L;
//    protected static final long DEFAULT_VERSION = 777L;
//    protected static final String DEFAULT_HOSTNAME = "localhost";
//
//    protected PeerList list = new PeerList();
//
//    @Test
//    public void shouldGetPeer()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//
//        final Peer peer = new Peer();
//
//        // when
//        list.get(1, peer);
//
//        // then
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("b");
//
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION);
//
//        assertThat(peer.state()).isEqualTo(DEFAULT_STATE);
//    }
//
//    @Test
//    public void shouldFindPeer()
//    {
//        // given
//        final Peer key = createPeer("d");
//
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//        addPeer(list, key);
//        addPeer(list, createPeer("e"));
//        addPeer(list, createPeer("f"));
//        addPeer(list, createPeer("g"));
//        addPeer(list, createPeer("h"));
//
//        // when
//        final int idx = list.find(key);
//
//        // then
//        assertThat(idx).isEqualTo(3);
//    }
//
//    @Test
//    public void shouldNotFindPeer()
//    {
//        // given
//        final Peer key = createPeer("z");
//
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("d"));
//        addPeer(list, createPeer("e"));
//        addPeer(list, createPeer("f"));
//        addPeer(list, createPeer("g"));
//        addPeer(list, createPeer("h"));
//
//        // when
//        final int idx = list.find(key);
//
//        // then
//        assertThat(idx).isEqualTo(-9);
//    }
//
//    @Test
//    public void shouldAppendPeer()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//
//        // assume
//        assertThat(list.size()).isEqualTo(3);
//
//        final Peer peer = createPeer("d");
//
//        // when
//        list.append(peer);
//
//        // then
//        final String[] hostnames = { "a", "b", "c", "d" };
//        assertPeerList(list, hostnames);
//    }
//
//    @Test
//    public void shouldInsertPeer()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("d"));
//        addPeer(list, createPeer("e"));
//        addPeer(list, createPeer("g"));
//        addPeer(list, createPeer("h"));
//
//        final Peer peer = createPeer("f", 9090, 888L, 111L, UP);
//
//        // when
//        list.insert(peer);
//
//        // then
//        assertThat(list.size()).isEqualTo(8);
//
//        final int idx = list.find(peer);
//        assertThat(idx).isEqualTo(5);
//
//        final Peer fetchedPeer = new Peer();
//        list.get(idx, fetchedPeer);
//
//        assertThat(fetchedPeer.endpoint()).isNotNull();
//        assertThat(fetchedPeer.endpoint().port()).isEqualTo(9090);
//        assertThat(fetchedPeer.endpoint().host()).isEqualTo("f");
//
//        assertThat(fetchedPeer.heartbeat()).isNotNull();
//        assertThat(fetchedPeer.heartbeat().generation()).isEqualTo(888L);
//        assertThat(fetchedPeer.heartbeat().version()).isEqualTo(111L);
//
//        assertThat(fetchedPeer.state()).isEqualTo(UP);
//    }
//
//    @Test
//    public void shouldSetPeer()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("d"));
//        addPeer(list, createPeer("e"));
//        addPeer(list, createPeer("f"));
//        addPeer(list, createPeer("g"));
//        addPeer(list, createPeer("h"));
//
//        final Peer update = createPeer("g", DEFAULT_PORT, 999L, 3L, NULL_VAL);
//
//        // when
//        list.set(update);
//
//        // then
//        final Peer fetchedPeer = new Peer();
//        list.get(6, fetchedPeer);
//
//        assertThat(fetchedPeer.endpoint()).isNotNull();
//        assertThat(fetchedPeer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(fetchedPeer.endpoint().host()).isEqualTo("g");
//
//        assertThat(fetchedPeer.heartbeat()).isNotNull();
//        assertThat(fetchedPeer.heartbeat().generation()).isEqualTo(999L);
//        assertThat(fetchedPeer.heartbeat().version()).isEqualTo(3L);
//
//        assertThat(fetchedPeer.state()).isEqualTo(NULL_VAL);
//    }
//
//    @Test
//    public void shoudClearList()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//
//        // when
//        list.clear();
//
//        // then
//        assertThat(list.size()).isEqualTo(0);
//        assertThat(list.getPeers().size()).isEqualTo(0);
//    }
//
//    @Test
//    public void shouldReturnSize()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//
//        // when
//        final int size = list.size();
//
//        // then
//        assertThat(size).isEqualTo(3);
//    }
//
//    @Test
//    public void shouldUpdateHeartbeat()
//    {
//        // given
//        final Peer toUpdate = createPeer("g");
//
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("d"));
//        addPeer(list, createPeer("e"));
//        addPeer(list, createPeer("f"));
//        addPeer(list, toUpdate);
//        addPeer(list, createPeer("h"));
//
//        // when
//        list.updateHeartbeat(toUpdate);
//
//        // then
//        final Peer fetchedPeer = new Peer();
//        list.get(6, fetchedPeer);
//
//        assertThat(fetchedPeer.heartbeat().version()).isEqualTo(DEFAULT_VERSION + 1);
//    }
//
//    @Test
//    public void shouldMergeUpdatesIntoEmptyList()
//    {
//        // given
//        final PeerList updates = new PeerList();
//
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//
//        // assume
//        assertThat(list.size()).isEqualTo(0);
//
//        // when
//        list.merge(updates.iterator(), null);
//
//        // then
//
//        final String[] listHostnames = { "a", "b", "c", "d" };
//        assertPeerList(list, listHostnames);
//    }
//
//    @Test
//    public void shouldMergePeersIntoDiff()
//    {
//        // given
//        final PeerList updates = new PeerList();
//        final PeerList diff = new PeerList();
//
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("b"));
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("d"));
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] diffHostnames = { "a", "b", "c", "d" };
//        assertPeerList(diff, diffHostnames);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBeforeExistingPeer()
//    {
//        // given
//        addPeer(list, createPeer("e"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        final String[] diffHostnames = { "e" };
//        assertPeerList(diff, diffHostnames);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBeforeMatchingPeer()
//    {
//        // given
//        addPeer(list, createPeer("e"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        assertPeerList(diff, new String[0]);
//    }
//
//    @Test
//    public void shouldMergeUpdatesAfterExistingPeer()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        final String[] diffHostnames = { "a" };
//        assertPeerList(diff, diffHostnames);
//    }
//
//    @Test
//    public void shouldMergeUpdatesAfterMatchingPeer()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        assertPeerList(diff, new String[0]);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBeforeAndAfterExistingPeer()
//    {
//        // given
//        addPeer(list, createPeer("c"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        final String[] diffHostnames = { "c" };
//        assertPeerList(diff, diffHostnames);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBeforeAndAfterMatchingPeer()
//    {
//        // given
//        addPeer(list, createPeer("c"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        assertPeerList(diff, new String[0]);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBetweenExistingPeers()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("e"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        final String[] diffHostnames = { "a", "e" };
//        assertPeerList(diff, diffHostnames);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBetweenMatchingPeers()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//        addPeer(list, createPeer("e"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e" };
//        assertPeerList(list, listHostnames);
//
//        assertPeerList(diff, new String[0]);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBeforeBetweenAndAfterExistingPeers()
//    {
//        // given
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("f"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//        addPeer(updates, createPeer("g"));
//        addPeer(updates, createPeer("h"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] listHostnames = { "a", "b", "c", "d", "e", "f", "g", "h" };
//        assertPeerList(list, listHostnames);
//
//        final String[] diffHostnames = { "c", "f" };
//        assertPeerList(diff, diffHostnames);
//    }
//
//    @Test
//    public void shouldMergeUpdatesBeforeBetweenAndAfterMatchingPeers()
//    {
//        // given
//        addPeer(list, createPeer("c"));
//        addPeer(list, createPeer("f"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//        addPeer(updates, createPeer("b"));
//        addPeer(updates, createPeer("c"));
//        addPeer(updates, createPeer("d"));
//        addPeer(updates, createPeer("e"));
//        addPeer(updates, createPeer("f"));
//        addPeer(updates, createPeer("g"));
//        addPeer(updates, createPeer("h"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        final String[] hostnames = { "a", "b", "c", "d", "e", "f", "g", "h" };
//        assertPeerList(list, hostnames);
//
//        assertPeerList(diff, new String[0]);
//    }
//
//    @Test
//    public void shouldUpdatePeerByGeneration()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a", DEFAULT_PORT, DEFAULT_GENERATION + 1, DEFAULT_VERSION, NULL_VAL));
//
//        // when
//        list.merge(updates.iterator(), null);
//
//        // then
//        final Peer peer = list.iterator().next();
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("a");
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION + 1);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//    }
//
//    @Test
//    public void shouldUpdatePeerByVersion()
//    {
//        // given
//        addPeer(list, createPeer("a"));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a", DEFAULT_PORT, DEFAULT_GENERATION, DEFAULT_VERSION + 1, NULL_VAL));
//
//        // when
//        list.merge(updates.iterator(), null);
//
//        // then
//        final Peer peer = list.iterator().next();
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("a");
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION + 1);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//    }
//
//    @Test
//    public void shouldIgnorePeerUpdateByGeneration()
//    {
//        // given
//        addPeer(list, createPeer("a", DEFAULT_PORT, DEFAULT_GENERATION + 1, DEFAULT_VERSION, NULL_VAL));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        Peer peer = list.iterator().next();
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("a");
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION + 1);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//
//        assertThat(diff.size()).isEqualTo(1);
//        peer = diff.iterator().next();
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("a");
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION + 1);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//    }
//
//    @Test
//    public void shouldIgnorePeerUpdateByVersion()
//    {
//        // given
//        addPeer(list, createPeer("a", DEFAULT_PORT, DEFAULT_GENERATION, DEFAULT_VERSION + 1, NULL_VAL));
//
//        final PeerList updates = new PeerList();
//        addPeer(updates, createPeer("a"));
//
//        final PeerList diff = new PeerList();
//
//        // when
//        list.merge(updates.iterator(), diff);
//
//        // then
//        Peer peer = list.iterator().next();
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("a");
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION + 1);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//
//        assertThat(diff.size()).isEqualTo(1);
//        peer = diff.iterator().next();
//        assertThat(peer.endpoint()).isNotNull();
//        assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//        assertThat(peer.endpoint().host()).isEqualTo("a");
//        assertThat(peer.heartbeat()).isNotNull();
//        assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION);
//        assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION + 1);
//        assertThat(peer.state()).isEqualTo(NULL_VAL);
//    }
//
//    @Test
//    public void shouldNotHaveNext()
//    {
//        assertThat(list.iterator().hasNext()).isFalse();
//    }
//
//    @Test
//    public void shouldHaveNext()
//    {
//        // given
//        addPeers(list);
//
//        // when + then
//        assertThat(list.iterator().hasNext()).isTrue();
//    }
//
//    @Test
//    public void shouldResetIterator()
//    {
//        // given
//        addPeers(list);
//
//        PeerListIterator iterator = list.iterator();
//
//        // assume
//        assertThat(iterator.next().endpoint().host()).isEqualTo("0");
//
//        // when
//        iterator = list.iterator();
//
//        // then
//        assertThat(iterator.next().endpoint().host()).isEqualTo("0");
//    }
//
//    @Test
//    public void shouldIterate()
//    {
//        // given
//        addPeers(list);
//
//        // when
//        final PeerListIterator iterator = list.iterator();
//
//        // then
//        assertThat(iterator.next().endpoint().host()).isEqualTo("0");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("1");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("2");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("3");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("4");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("5");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("6");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("7");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("8");
//        assertThat(iterator.next().endpoint().host()).isEqualTo("9");
//    }
//
//    @Test
//    public void shouldAddPeerWhenIterating()
//    {
//        // given
//        addPeers(list);
//
//        final PeerListIterator iterator = list.iterator();
//        iterator.next();
//        iterator.next();
//        iterator.next();
//
//        // when
//        iterator.add(createPeer("a"));
//
//        // then
//        final String[] hostnames = { "0", "1", "a", "2", "3", "4", "5", "6", "7", "8", "9" };
//        assertPeerList(list, hostnames);
//    }
//
//    @Test
//    public void shouldReturnSamePeer()
//    {
//        // given
//        addPeers(list);
//
//        final PeerListIterator iterator = list.iterator();
//        iterator.next();
//        iterator.next();
//        Peer peer = iterator.next();
//
//        // assume
//        assertThat(peer.endpoint().host()).isEqualTo("2");
//
//        iterator.add(createPeer("a"));
//
//        // when
//        peer = iterator.next();
//
//        // then
//        assertThat(peer.endpoint().host()).isEqualTo("2");
//    }
//
//    @Test
//    public void shouldSetPeerWhenIterating()
//    {
//        // given
//        addPeers(list);
//
//        final PeerListIterator iterator = list.iterator();
//        iterator.next();
//        iterator.next();
//        iterator.next();
//
//        // when
//        iterator.set(createPeer("a"));
//
//        // then
//        final String[] hostnames = { "0", "1", "a", "3", "4", "5", "6", "7", "8", "9" };
//        assertPeerList(list, hostnames);
//    }
//
//    protected void assertPeerList(PeerList list, String[] hostnames)
//    {
//        assertThat(list.size()).isEqualTo(hostnames.length);
//        final PeerListIterator iterator = list.iterator();
//        int position = 0;
//
//        while (iterator.hasNext())
//        {
//            final Peer peer = iterator.next();
//
//            assertThat(peer.endpoint()).isNotNull();
//            assertThat(peer.endpoint().port()).isEqualTo(DEFAULT_PORT);
//            assertThat(peer.endpoint().host()).isEqualTo(hostnames[position]);
//
//            assertThat(peer.heartbeat()).isNotNull();
//            assertThat(peer.heartbeat().generation()).isEqualTo(DEFAULT_GENERATION);
//            assertThat(peer.heartbeat().version()).isEqualTo(DEFAULT_VERSION);
//
//            assertThat(peer.state()).isEqualTo(DEFAULT_STATE);
//
//            position++;
//        }
//    }
//
//    protected void addPeer(PeerList dst, Peer peer)
//    {
//        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[Peer.MAX_PEER_LENGTH]);
//        peer.write(buffer, 0);
//        dst.getPeers().add(buffer, 0, peer.getLength());
//    }
//
//    protected Peer createPeer(String hostname)
//    {
//        return createPeer(hostname, DEFAULT_PORT, DEFAULT_GENERATION, DEFAULT_VERSION, DEFAULT_STATE);
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
//
//    protected void addPeers(PeerList dst)
//    {
//        for (int i = 0; i < 10; i++)
//        {
//            addPeer(dst, createPeer(String.valueOf(i)));
//        }
//    }

}
