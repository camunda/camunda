/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.cleanup;

import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.commit;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The history cleanup deletes the data of expired root process instances table by
 * table. Four of those deletes cascade ({@code ON DELETE CASCADE}) into child tables in the same
 * statement: process instances into their tags, user tasks into their candidate users, candidate
 * groups and tags, decision instances into their inputs and outputs, agent instances into their
 * element instances. Meanwhile the flush of the same partition writes data of a running root whose
 * key lies between the expired ones, as roots that ran at the same time have neighbouring keys.
 *
 * <p>Each test seeds 3,000 roots with their data, expires every fifth of the 500 roots in the
 * middle (100 roots, so each cascade stays below SQL Server's lock escalation at about 5,000
 * locks), and lets the flush write for the running root right before an expired one.
 *
 * <p><b>Interleaving</b> (time flows down):
 *
 * <pre>
 *   P1 cleanup                        P1 flush
 *   DELETE the expired roots' rows (+ children by cascade), stop before commit (holds its locks)
 *                                     write rows of the running root, up to its commit
 *   then: the flush did not wait for a lock
 * </pre>
 *
 * <p><b>Today.</b>
 *
 * <ul>
 *   <li>SQL Server: the cascades run with key-range locks (RangeX-X on every deleted child, at
 *       SERIALIZABLE whatever the session's isolation), and an insert into a locked range waits
 *       (RangeI-N): changing a running user task's candidates, replacing a running agent instance's
 *       element instances. The decision cascade (14 children per instance) escalates to a table
 *       lock already at 100 decision instances.
 *   <li>MySQL, MariaDB: REPEATABLE READ gap locks, on the parent's root key index (decision
 *       instances) or on the child's index (candidates, agent element instances; MySQL only).
 *   <li>Process instance tags block nowhere: tags are written once, with a new key.
 *   <li>PostgreSQL, Oracle, H2: only the deleted rows are locked.
 * </ul>
 *
 * <p><b>Fix direction.</b> SQL Server: delete the children explicitly, in chunks, before the
 * parent, so the cascade (and its range locks) finds nothing. MySQL/MariaDB: READ COMMITTED.
 */
@RdbmsConcurrencyTest
class HistoryCleanupCascadeBlockingConcurrencyIT {

  @TestTemplate
  void shouldNotBlockAddingAChildInstanceWithTagsToARunningRoot(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: roots with tags; the cleanup deletes the expired roots, cascading into their tags
      final var roots =
          Roots.seed(
              kit,
              (writers, root) ->
                  writers.getProcessInstanceWriter().create(processInstance(root, root)),
              "PROCESS_INSTANCE",
              "PROCESS_INSTANCE_TAG");
      final var cleanup =
          kit.startCleanup(
              1, writers -> writers.getProcessInstanceWriter().deleteByKeys(roots.expired()));
      final var flush =
          kit.startWrite(
              1,
              writers ->
                  writers
                      .getProcessInstanceWriter()
                      .create(processInstance(nextKey(1), roots.running())));

      // when: the cleanup deletes and holds its locks; the flush adds a child instance with tags
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  @TestTemplate
  @FailsToday(
      on = MSSQL,
      reason = "the cascade's key-range locks (SERIALIZABLE) cover the range the insert goes into")
  @FailsToday(
      on = MYSQL,
      reason = "REPEATABLE READ gap locks of the cleanup cover the range the insert goes into")
  void shouldNotBlockChangingTheCandidatesOfARunningUserTask(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: a user task with candidates and tags per root; the cleanup deletes the expired
      // roots' user tasks, cascading into their candidates and tags
      final Map<Long, Long> userTaskOfRoot = new HashMap<>();
      final var roots =
          Roots.seed(
              kit,
              (writers, root) -> {
                final long userTask = nextKey(1);
                userTaskOfRoot.put(root, userTask);
                writers.getUserTaskWriter().create(userTask(userTask, root, "a"));
              },
              "USER_TASK",
              "CANDIDATE_USER",
              "CANDIDATE_GROUP",
              "USER_TASK_TAG");
      final var cleanup =
          kit.startCleanup(
              1,
              writers ->
                  writers
                      .getUserTaskWriter()
                      .deleteRootProcessInstanceRelatedData(roots.expired(), 10_000));
      final long running = roots.running();
      final var flush =
          kit.startWrite(
              1,
              writers ->
                  writers
                      .getUserTaskWriter()
                      .update(userTask(userTaskOfRoot.get(running), running, "b")));

      // when: the cleanup deletes and holds its locks; the flush replaces the candidates of the
      // running root's user task (delete and insert)
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  @TestTemplate
  @FailsToday(
      on = MSSQL,
      reason = "the cascade into 1,400 inputs and outputs escalates to a table lock")
  @FailsToday(
      on = {MYSQL, MARIADB},
      reason = "REPEATABLE READ gap locks of the cleanup cover the range the insert goes into")
  void shouldNotBlockAddingADecisionInstanceToARunningRoot(final CamundaRdbmsTestApplication app)
      throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: a decision instance with inputs and outputs per root; the cleanup deletes the
      // expired roots' decision instances, cascading into their inputs and outputs
      final var roots =
          Roots.seed(
              kit,
              (writers, root) -> writers.getDecisionInstanceWriter().create(decisionInstance(root)),
              "DECISION_INSTANCE",
              "DECISION_INSTANCE_INPUT",
              "DECISION_INSTANCE_OUTPUT");
      final var cleanup =
          kit.startCleanup(
              1,
              writers ->
                  writers
                      .getDecisionInstanceWriter()
                      .deleteRootProcessInstanceRelatedData(roots.expired(), 10_000));
      final var flush =
          kit.startWrite(
              1,
              writers ->
                  writers.getDecisionInstanceWriter().create(decisionInstance(roots.running())));

      // when: the cleanup deletes and holds its locks; the flush adds a decision instance of the
      // running root
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  @TestTemplate
  @FailsToday(
      on = MSSQL,
      reason = "the cascade's key-range locks (SERIALIZABLE) cover the range the insert goes into")
  @FailsToday(
      on = MYSQL,
      reason = "REPEATABLE READ gap locks of the cleanup cover the range the insert goes into")
  void shouldNotBlockAddingElementInstancesToARunningAgentInstance(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: an agent instance with element instances per root; the cleanup deletes the expired
      // roots' agent instances, cascading into their element instances
      final Map<Long, Long> agentOfRoot = new HashMap<>();
      final var roots =
          Roots.seed(
              kit,
              (writers, root) -> {
                final long agent = nextKey(1);
                agentOfRoot.put(root, agent);
                writers.getAgentInstanceWriter().create(agentInstance(agent, root));
              },
              "AGENT_INSTANCE",
              "AGENT_INSTANCE_ELEMENT_INSTANCE");
      final var cleanup =
          kit.startCleanup(
              1,
              writers ->
                  writers
                      .getAgentInstanceWriter()
                      .deleteRootProcessInstanceRelatedData(roots.expired(), 10_000));
      final long running = roots.running();
      final var flush =
          kit.startWrite(
              1,
              writers ->
                  writers
                      .getAgentInstanceWriter()
                      .update(agentInstance(agentOfRoot.get(running), running)));

      // when: the cleanup deletes and holds its locks; the flush replaces the element instances of
      // the running root's agent instance (delete and insert)
      cleanup.stepTo(commit());
      final var progress = flush.runTo(commit());
      cleanup.release();
      flush.release();

      // then: the flush got to its commit without waiting for the cleanup's locks
      assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse();
      kit.assertNoErrors(cleanup, flush);
    }
  }

  /** A process instance of the root, with three tags. */
  private static ProcessInstanceDbModel processInstance(final long key, final long root) {
    return ProcessInstanceFixtures.createRandomized(
        b ->
            b.processInstanceKey(key)
                .rootProcessInstanceKey(root)
                .parentProcessInstanceKey(key == root ? null : root)
                .partitionId(1)
                .tags(Set.of("tag1", "tag2", "tag3")));
  }

  /** A user task of the root with two candidate users, two candidate groups and two tags. */
  private static UserTaskDbModel userTask(
      final long userTask, final long root, final String candidates) {
    return UserTaskFixtures.createRandomized(
        b ->
            b.userTaskKey(userTask)
                .processInstanceKey(root)
                .rootProcessInstanceKey(root)
                .candidateUsers(List.of(candidates + "-user1", candidates + "-user2"))
                .candidateGroups(List.of(candidates + "-group1", candidates + "-group2"))
                .tags(Set.of("tag1", "tag2")));
  }

  /** A decision instance of the root with seven inputs and seven outputs. */
  private static DecisionInstanceDbModel decisionInstance(final long root) {
    final long key = nextKey(1);
    final String id = key + "-1";
    return DecisionInstanceFixtures.createRandomized(
        b ->
            b.decisionInstanceKey(key)
                .decisionInstanceId(id)
                .processInstanceKey(root)
                .rootProcessInstanceKey(root)
                .evaluatedInputs(
                    IntStream.range(0, 7)
                        .mapToObj(i -> new EvaluatedInput(id, "input-" + i, "Input", "1"))
                        .toList())
                .evaluatedOutputs(
                    IntStream.range(0, 7)
                        .mapToObj(
                            i -> new EvaluatedOutput(id, "output-" + i, "Output", "1", "rule", i))
                        .toList()));
  }

  /** An agent instance of the root with three new element instances. */
  private static AgentInstanceDbModel agentInstance(final long agent, final long root) {
    return AgentInstanceFixtures.createRandomAgentInstance(
        b ->
            b.agentInstanceKey(agent)
                .processInstanceKey(root)
                .rootProcessInstanceKey(root)
                .elementInstanceKeys(List.of(nextKey(1), nextKey(1), nextKey(1))));
  }

  /**
   * 3,000 seeded roots in key order; of the 500 in the middle every fifth is expired (100 roots).
   */
  private record Roots(List<Long> seeded, List<Long> expired, long running) {

    /**
     * Seeds the roots, writing each root's data with the given writes, 200 roots per flush, then
     * analyzes the given tables.
     */
    static Roots seed(
        final RdbmsConcurrencyKit kit,
        final BiConsumer<RdbmsWriters, Long> data,
        final String... tables)
        throws Exception {
      final List<Long> seeded = new ArrayList<>();
      for (int chunk = 0; chunk < 15; chunk++) {
        kit.write(
            1,
            writers -> {
              for (int i = 0; i < 200; i++) {
                final long root = nextKey(1);
                seeded.add(root);
                data.accept(writers, root);
              }
            });
      }
      // fresh planner statistics, as a long-running installation has them: right after seeding,
      // MySQL plans with those of a near-empty table and scans the primary key instead
      kit.analyze(tables);
      final var middle = seeded.subList(1_250, 1_750);
      final List<Long> expired = new ArrayList<>();
      for (int i = 0; i < middle.size(); i += 5) {
        expired.add(middle.get(i));
      }
      // the root right before an expired one: its rows lie right before the deleted ones
      return new Roots(seeded, expired, middle.get(249));
    }
  }
}
