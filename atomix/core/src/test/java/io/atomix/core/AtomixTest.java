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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftPartitionGroupConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class AtomixTest {

  private static final int TIMEOUT_IN_S = 90;

  @Rule public final AtomixRule atomixRule = new AtomixRule();

  @Test
  public void testStopStartConsensus() throws Exception {
    // given
    final var atomix =
        atomixRule
            .startAtomix(
                1,
                Arrays.asList(1),
                (builder) -> {
                  final var groupConfig =
                      new RaftPartitionGroupConfig()
                          .setName("raft")
                          .setPartitionSize(3)
                          .setPartitions(7)
                          .setMembers(Set.of("1"))
                          .setStorageConfig(
                              new RaftStorageConfig()
                                  .setDirectory(
                                      new File(atomixRule.getDataDir(), "start-stop-consensus")
                                          .getPath())
                                  .setPersistedSnapshotStoreFactory(
                                      new NoopSnapshotStoreFactory()));

                  final var raftPartitionGroup = new RaftPartitionGroup(groupConfig);
                  return builder.withPartitionGroups(raftPartitionGroup).build();
                })
            .get(TIMEOUT_IN_S, TimeUnit.SECONDS);

    // when
    final var stopFuture = atomix.stop();

    // then
    assertThat(stopFuture).succeedsWithin(TIMEOUT_IN_S, TimeUnit.SECONDS);
    assertThat(stopFuture).isDone();
  }

  @Test
  public void shouldFailStartAfterStop() throws Exception {
    // given
    final var atomix =
        atomixRule
            .startAtomix(1, Arrays.asList(1), AtomixBuilder::build)
            .get(TIMEOUT_IN_S, TimeUnit.SECONDS);
    atomix.stop().get(TIMEOUT_IN_S, TimeUnit.SECONDS);

    // when
    try {
      atomix.start().get(TIMEOUT_IN_S, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (final ExecutionException ex) {
      // then
      assertTrue(ex.getCause() instanceof IllegalStateException);
      assertEquals("Atomix instance shutdown", ex.getCause().getMessage());
    }
  }
}
