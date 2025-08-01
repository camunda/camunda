/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Utilities to help write tests; as these are utils, everything is public by default */
@SuppressWarnings("WeakerAccess")
public class ZeebeTestHelper {

  private static final long DEFAULT_TIMEOUT_MS = 10_000;
  private final Collection<ZeebeTestNode> nodes;

  public ZeebeTestHelper(final Collection<ZeebeTestNode> nodes) {
    this.nodes = nodes;
  }

  public ZeebeLogAppender awaitLeaderAppender(final int partitionId) {
    final RaftPartitionServer server = awaitLeaderServer(partitionId);
    return await(server::getAppender);
  }

  public RaftPartitionServer awaitLeaderServer(final int partitionId) {
    return awaitLeader(partitionId).getPartitionServer(partitionId);
  }

  public ZeebeTestNode awaitLeader(final int partitionId) {
    return await(() -> getLeader(partitionId));
  }

  public ZeebeTestNode awaitLeader(final int partitionId, final Collection<ZeebeTestNode> nodes) {
    return await(() -> getLeader(partitionId, nodes));
  }

  public Optional<ZeebeTestNode> getLeader(final int partitionId) {
    return getLeader(partitionId, nodes);
  }

  public Optional<ZeebeTestNode> getLeader(
      final int partitionId, final Collection<ZeebeTestNode> nodes) {
    return nodes.stream()
        .filter(n -> n.getPartition(partitionId).getRole() == Role.LEADER)
        .findFirst();
  }

  public void awaitAllContain(final int partitionId, final IndexedRaftLogEntry indexed) {
    awaitAllContains(nodes, partitionId, indexed);
  }

  public void awaitAllContains(
      final Collection<ZeebeTestNode> nodes,
      final int partitionId,
      final IndexedRaftLogEntry indexed) {
    await(() -> nodes.stream().allMatch(node -> containsIndexed(node, partitionId, indexed)));
  }

  public boolean containsIndexed(
      final ZeebeTestNode node, final int partitionId, final IndexedRaftLogEntry indexed) {
    final RaftPartitionServer partition = node.getPartitionServer(partitionId);
    return containsIndexed(partition, indexed);
  }

  public boolean containsIndexed(
      final RaftPartitionServer partition, final IndexedRaftLogEntry indexed) {
    try (final RaftLogReader reader = partition.openReader()) {
      reader.seek(indexed.index());

      if (reader.hasNext()) {
        final IndexedRaftLogEntry entry = reader.next();
        if (entry.index() == indexed.index()) {
          return isEntryEqualTo(entry, indexed);
        }
      }
    }

    return false;
  }

  public boolean isEntryEqualTo(
      final IndexedRaftLogEntry indexed, final IndexedRaftLogEntry other) {
    return indexed.equals(other);
  }

  public void await(final BooleanSupplier predicate) {
    final long tries = Duration.ofMillis(DEFAULT_TIMEOUT_MS).toNanos() / 100;
    boolean result = predicate.getAsBoolean();
    for (long i = 0; i < tries && !result; i++) {
      LockSupport.parkNanos(100);
      result = predicate.getAsBoolean();
    }

    assertThat(result).isTrue();
  }

  public void awaitContains(
      final ZeebeTestNode node, final int partitionId, final IndexedRaftLogEntry indexed) {
    await(() -> containsIndexed(node, partitionId, indexed));
  }

  public <T> T await(final Supplier<Optional<T>> supplier) {
    await(supplier, Optional::isPresent);
    final Optional<T> result = supplier.get();
    return result.get();
  }

  public <T> T await(final Supplier<T> supplier, final Predicate<T> condition) {
    await(() -> condition.test(supplier.get()));
    return supplier.get();
  }
}
