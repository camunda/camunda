/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.atomix.core.profile.ConsensusProfile;
import io.atomix.core.profile.Profile;
import io.atomix.utils.concurrent.Futures;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Atomix test. */
public class AtomixTest extends AbstractAtomixTest {

  private List<Atomix> instances;

  @Before
  public void setupInstances() throws Exception {
    setupAtomix();
    instances = new ArrayList<>();
  }

  @After
  public void teardownInstances() throws Exception {
    final List<CompletableFuture<Void>> futures =
        instances.stream().map(Atomix::stop).collect(Collectors.toList());
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
          .get(30, TimeUnit.SECONDS);
    } catch (final Exception e) {
      // Do nothing
    }
  }

  protected CompletableFuture<Atomix> startAtomix(
      final int id, final List<Integer> persistentNodes, final Profile... profiles) {
    return startAtomix(id, persistentNodes, b -> b.withProfiles(profiles).build());
  }

  /** Creates and starts a new test Atomix instance. */
  protected CompletableFuture<Atomix> startAtomix(
      final int id,
      final List<Integer> persistentIds,
      final Function<AtomixBuilder, Atomix> builderFunction) {
    final Atomix atomix = createAtomix(id, persistentIds, builderFunction);
    instances.add(atomix);
    return atomix.start().thenApply(v -> atomix);
  }

  /** Creates and starts a new test Atomix instance. */
  protected CompletableFuture<Atomix> startAtomix(
      final int id,
      final List<Integer> persistentIds,
      final Properties properties,
      final Profile... profiles) {
    final Atomix atomix =
        createAtomix(
            id, persistentIds, properties, builder -> builder.withProfiles(profiles).build());
    instances.add(atomix);
    return atomix.start().thenApply(v -> atomix);
  }

  /** Tests scaling up a cluster. */
  @Test
  public void testScaleUpPersistent() throws Exception {
    final Atomix atomix1 =
        startAtomix(
                1,
                Arrays.asList(1),
                ConsensusProfile.builder()
                    .withMembers("1")
                    .withDataPath(new File(getDataDir(), "scale-up"))
                    .build())
            .get(30, TimeUnit.SECONDS);
    final Atomix atomix2 =
        startAtomix(2, Arrays.asList(1, 2), Profile.client()).get(30, TimeUnit.SECONDS);
    final Atomix atomix3 =
        startAtomix(3, Arrays.asList(1, 2, 3), Profile.client()).get(30, TimeUnit.SECONDS);
  }

  @Test
  public void testStopStartConsensus() throws Exception {
    final Atomix atomix1 =
        startAtomix(
                1,
                Arrays.asList(1),
                ConsensusProfile.builder()
                    .withMembers("1")
                    .withDataPath(new File(getDataDir(), "start-stop-consensus"))
                    .build())
            .get(30, TimeUnit.SECONDS);
    atomix1.stop().get(30, TimeUnit.SECONDS);
    try {
      atomix1.start().get(30, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (final ExecutionException ex) {
      assertTrue(ex.getCause() instanceof IllegalStateException);
      assertEquals("Atomix instance shutdown", ex.getCause().getMessage());
    }
  }

  /** Tests a client joining and leaving the cluster. */
  @Test
  public void testClientJoinLeaveConsensus() throws Exception {
    testClientJoinLeave(
        ConsensusProfile.builder()
            .withMembers("1", "2", "3")
            .withDataPath(new File(new File(getDataDir(), "join-leave"), "1"))
            .build(),
        ConsensusProfile.builder()
            .withMembers("1", "2", "3")
            .withDataPath(new File(new File(getDataDir(), "join-leave"), "2"))
            .build(),
        ConsensusProfile.builder()
            .withMembers("1", "2", "3")
            .withDataPath(new File(new File(getDataDir(), "join-leave"), "3"))
            .build());
  }

  private void testClientJoinLeave(final Profile... profiles) throws Exception {
    final List<CompletableFuture<Atomix>> futures = new ArrayList<>();
    futures.add(startAtomix(1, Arrays.asList(1, 2, 3), profiles[0]));
    futures.add(startAtomix(2, Arrays.asList(1, 2, 3), profiles[1]));
    futures.add(startAtomix(3, Arrays.asList(1, 2, 3), profiles[2]));
    Futures.allOf(futures).get(30, TimeUnit.SECONDS);

    final TestClusterMembershipEventListener dataListener =
        new TestClusterMembershipEventListener();
    instances.get(0).getMembershipService().addListener(dataListener);

    final Atomix client1 =
        startAtomix(4, Arrays.asList(1, 2, 3), Profile.client()).get(30, TimeUnit.SECONDS);
    assertEquals(1, client1.getPartitionService().getPartitionGroups().size());

    // client1 added to data node
    final ClusterMembershipEvent event1 = dataListener.event();
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, event1.type());

    Thread.sleep(1000);

    final TestClusterMembershipEventListener clientListener =
        new TestClusterMembershipEventListener();
    client1.getMembershipService().addListener(clientListener);

    final Atomix client2 =
        startAtomix(5, Arrays.asList(1, 2, 3), Profile.client()).get(30, TimeUnit.SECONDS);
    assertEquals(1, client2.getPartitionService().getPartitionGroups().size());

    // client2 added to data node
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, dataListener.event().type());

    // client2 added to client node
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, clientListener.event().type());

    client2.stop().get(30, TimeUnit.SECONDS);

    // client2 removed from data node
    assertEquals(ClusterMembershipEvent.Type.REACHABILITY_CHANGED, dataListener.event().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_REMOVED, dataListener.event().type());

    // client2 removed from client node
    assertEquals(ClusterMembershipEvent.Type.REACHABILITY_CHANGED, clientListener.event().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_REMOVED, clientListener.event().type());
  }

  /** Tests a client properties. */
  @Test
  public void testClientProperties() throws Exception {
    final List<CompletableFuture<Atomix>> futures = new ArrayList<>();
    futures.add(
        startAtomix(
            1,
            Arrays.asList(1, 2, 3),
            ConsensusProfile.builder()
                .withMembers("1", "2", "3")
                .withDataPath(new File(new File(getDataDir(), "client-properties"), "1"))
                .build()));
    futures.add(
        startAtomix(
            2,
            Arrays.asList(1, 2, 3),
            ConsensusProfile.builder()
                .withMembers("1", "2", "3")
                .withDataPath(new File(new File(getDataDir(), "client-properties"), "2"))
                .build()));
    futures.add(
        startAtomix(
            3,
            Arrays.asList(1, 2, 3),
            ConsensusProfile.builder()
                .withMembers("1", "2", "3")
                .withDataPath(new File(new File(getDataDir(), "client-properties"), "3"))
                .build()));
    Futures.allOf(futures).get(30, TimeUnit.SECONDS);

    final TestClusterMembershipEventListener dataListener =
        new TestClusterMembershipEventListener();
    instances.get(0).getMembershipService().addListener(dataListener);

    final Properties properties = new Properties();
    properties.setProperty("a-key", "a-value");
    final Atomix client1 =
        startAtomix(4, Arrays.asList(1, 2, 3), properties, Profile.client())
            .get(30, TimeUnit.SECONDS);
    assertEquals(1, client1.getPartitionService().getPartitionGroups().size());

    // client1 added to data node
    final ClusterMembershipEvent event1 = dataListener.event();
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, event1.type());

    final Member member = event1.subject();

    assertNotNull(member.properties());
    assertEquals(1, member.properties().size());
    assertEquals("a-value", member.properties().get("a-key"));
  }

  private static class TestClusterMembershipEventListener
      implements ClusterMembershipEventListener {

    private final BlockingQueue<ClusterMembershipEvent> queue = new LinkedBlockingQueue<>();

    @Override
    public void event(final ClusterMembershipEvent event) {
      try {
        queue.put(event);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    public boolean eventReceived() {
      return !queue.isEmpty();
    }

    public ClusterMembershipEvent event() throws InterruptedException, TimeoutException {
      final ClusterMembershipEvent event = queue.poll(15, TimeUnit.SECONDS);
      if (event == null) {
        throw new TimeoutException();
      }
      return event;
    }
  }
}
