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
package io.zeebe.raft.util;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftClusterRule implements TestRule {

  public static final int DEFAULT_RETRIES = 20;
  public static final int COMMITTED_RETRIES = 40;
  public static final int ALL_COMMITTED_RETRIES = 100;

  public static final Logger LOG = LoggerFactory.getLogger("io.zeebe.raft.test");

  protected final RecordMetadata metadata = new RecordMetadata();

  private final ActorSchedulerRule actorScheduler;
  private final ServiceContainerRule serviceContainerRule;
  private final List<RaftRule> rafts;

  public RaftClusterRule(
      final ActorSchedulerRule actorScheduler,
      ServiceContainerRule serviceContainerRule,
      final RaftRule... rafts) {
    this.actorScheduler = actorScheduler;
    this.serviceContainerRule = serviceContainerRule;
    this.rafts = rafts != null ? new ArrayList<>(Arrays.asList(rafts)) : Collections.emptyList();
  }

  @Override
  public Statement apply(Statement base, final Description description) {
    final List<TestRule> rules = new ArrayList<>();
    rules.add(actorScheduler);
    rules.add(serviceContainerRule);
    rules.addAll(rafts);
    rules.add(
        new ExternalResource() {
          @Override
          protected void before() throws Throwable {
            // register node endpoints between all rafts
            final int size = rafts.size();
            for (int from = 0; from < size - 1; from++) {
              for (int to = from + 1; to < size; to++) {
                rafts.get(from).reconnectTo(rafts.get(to));
              }
            }
          }
        });

    Collections.reverse(rules);

    for (final TestRule rule : rules) {
      base = rule.apply(base, description);
    }

    return base;
  }

  public List<RaftRule> getRafts() {
    return rafts;
  }

  public RaftClusterRule registerRaft(final RaftRule raft) {
    raft.clearSubscription();

    rafts.forEach(raft::reconnectTo);

    this.rafts.add(raft);

    return this;
  }

  public RaftClusterRule registerRafts(final RaftRule... rafts) {
    for (final RaftRule raft : rafts) {
      registerRaft(raft);
    }

    return this;
  }

  public RaftClusterRule removeRaft(final RaftRule raft) {
    Loggers.RAFT_LOGGER.debug("Interrupt connections for node {}", raft.getNodeId());

    final RaftRule[] otherRafts = getOtherRafts(raft);
    Arrays.stream(otherRafts).forEach(raft::interruptConnectionTo);

    this.rafts.remove(raft);

    return this;
  }

  public RaftClusterRule removeRafts(final RaftRule... rafts) {
    for (final RaftRule raft : rafts) {
      removeRaft(raft);
    }

    return this;
  }

  public RaftRule[] getOtherRafts(RaftRule toBeRemoved) {
    final RaftRule[] other = new RaftRule[rafts.size() - 1];

    int idx = 0;
    for (RaftRule rule : rafts) {
      if (!rule.equals(toBeRemoved)) {
        other[idx] = rule;
        idx++;
      }
    }
    return other;
  }

  public void awaitRaftState(final RaftRule raft, final RaftState state) {
    awaitCondition(
        () -> raft.getState() == state, "Failed to wait for %s to become %s", raft, state);
  }

  public void awaitAllJoined() {
    awaitCondition(
        () -> rafts.stream().allMatch(RaftRule::isJoined),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for all rafts to join");
  }

  public void awaitEventCommitted(final RaftRule raftToWait, final EventInfo eventInfo) {
    awaitCondition(
        () -> raftToWait.eventCommitted(eventInfo),
        COMMITTED_RETRIES,
        "Failed to wait for commit of event %s with message on raft %s",
        eventInfo,
        raftToWait);
  }

  public void awaitEventCommittedOnAll(final EventInfo eventInfo) {
    awaitCondition(
        () -> rafts.stream().allMatch(raft -> raft.eventCommitted(eventInfo)),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for commit of event %s with message on all rafts",
        eventInfo);
  }

  public void awaitEventsCommittedOnAll(final String... messages) {
    awaitCondition(
        () -> rafts.stream().allMatch(raft -> raft.eventsCommitted(messages)),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for events %s to be commit on all rafts",
        Arrays.asList(messages));
  }

  public void awaitEventAppendedOnAll(final EventInfo eventInfo) {
    awaitCondition(
        () -> rafts.stream().allMatch(raft -> raft.eventAppended(eventInfo)),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for commit of event %s with message on all rafts",
        eventInfo);
  }

  public void awaitInitialEventCommittedOnAll(final int term) {
    awaitCondition(
        () -> rafts.stream().allMatch(raft -> raft.eventCommitted(term, ValueType.NOOP)),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for initial event of term %d to be committed on all log streams",
        term);
  }

  public void awaitRaftEventCommittedOnAll(final int term) {
    awaitRaftEventCommittedOnAll(term, rafts.toArray(new RaftRule[rafts.size()]));
  }

  public void awaitRaftEventCommittedOnAll(final int term, final RaftRule... members) {
    awaitCondition(
        () -> rafts.stream().allMatch(raft -> raft.raftEventCommitted(term, members)),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for raft event of term %d with members %s to be committed on all log streams",
        term,
        Arrays.asList(members));
  }

  public RaftRule awaitLeader() {
    // ensure that all members joined the cluster before continuing
    awaitAllJoined();

    return awaitCondition(
        () -> rafts.stream().filter(RaftRule::isLeader).findAny(),
        ALL_COMMITTED_RETRIES,
        "Failed to wait for a node to become leader in the cluster");
  }

  public void awaitClusterSize(int members) {
    awaitCondition(
        () -> rafts.stream().filter(r -> r.getMemberSize() >= members - 1).count() == members,
        ALL_COMMITTED_RETRIES,
        "timeout while awaiting clustersize");
  }

  public void printLogEntries(final boolean readUncommitted) {
    rafts.forEach(r -> printLogEntries(r, readUncommitted));
  }

  public void printLogEntries(final RaftRule raft, final boolean readUncommitted) {
    LOG.error("Log entries for raft node {}", raft.getNodeId());

    final LogStream logStream = raft.getLogStream();
    final long commitPosition = logStream.getCommitPosition();
    final BufferedLogStreamReader reader = new BufferedLogStreamReader(logStream);
    reader.seekToFirstEvent();

    while (reader.hasNext()) {
      final LoggedEvent next = reader.next();
      next.readMetadata(metadata);

      String message = "";

      if (metadata.getValueType() == ValueType.NULL_VAL) {
        try {
          message =
              ", message: "
                  + bufferAsString(
                      next.getValueBuffer(), next.getValueOffset(), next.getValueLength());
        } catch (final Exception e) {
          // ignore
        }
      }

      LOG.error(
          "Event { position: {}, term: {}, type: {}, committed: {}{} }",
          next.getPosition(),
          next.getRaftTerm(),
          metadata.getValueType(),
          next.getPosition() <= commitPosition,
          message);
    }
  }

  protected void awaitCondition(
      final BooleanSupplier supplier, final String message, final Object... args) {
    awaitCondition(supplier, DEFAULT_RETRIES, message, args);
  }

  protected void awaitCondition(
      final BooleanSupplier supplier,
      final int retires,
      final String message,
      final Object... args) {
    try {
      TestUtil.waitUntil(supplier, retires, message, args);
    } catch (final Throwable e) {
      printLogEntries(true);
      throw e;
    }
  }

  protected <T> T awaitCondition(
      final Supplier<Optional<T>> supplier,
      final int retires,
      final String message,
      final Object... args) {
    awaitCondition(() -> supplier.get().isPresent(), retires, message, args);

    return supplier.get().orElseThrow(() -> new AssertionError("Failed get retrieve result"));
  }
}
