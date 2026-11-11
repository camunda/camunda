/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.AbstractJobStateContractTest;
import io.camunda.zeebe.engine.state.instance.JobStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the job state contract against PostgreSQL, plus the tests for what is specific to the
 * relational store: the row and its lookup columns, rollback, and the scans of the lookups, which
 * read the committed rows in pages and merge them with the staged writes.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition.
 */
abstract class AbstractRdbmsJobStateTest extends AbstractJobStateContractTest {

  /** How the session under test writes: directly, or behind the commit. */
  abstract StateMode mode();

  private static final String TENANT_ID = "job-tenant";
  private static final String OTHER_TENANT_ID = "other-job-tenant";

  private static EngineStateDatabase database;

  private final QueryCounter queryCounter = new QueryCounter();
  private JdbcTransactionContext transactionContext;
  private int partitionId;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableJobState createJobState() {
    database.observed().reset();
    database.observed().decorate(queryCounter::wrap);
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    return new JobStateImpl(
        new RdbmsJobStore(database.entitySession(mode(), transactionContext, partitionId)));
  }

  @AfterEach
  void endTransaction() throws Exception {
    transactionContext.getCurrentTransaction().rollback();
    database.observed().reset();
  }

  @Test
  void shouldWriteRowWithMetadataOnCommit() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    final var record =
        jobRecord(TENANT_ID, 5)
            .setProcessInstanceKey(200)
            .setRootProcessInstanceKey(100)
            .setElementInstanceKey(201)
            .setBpmnProcessId("process")
            .setElementId("task")
            .setJobKind(JobKind.EXECUTION_LISTENER)
            .setVariables(MsgPackUtil.asMsgPack("{'a': 1}"));

    // when
    createJob(1, record);
    assertThat(rows("SELECT job_key FROM CAM_RU_JOB")).isEmpty();
    transaction.commit();

    // then
    assertThat(
            rows(
                "SELECT job_key, state, type, tenant_id, activatable_type, inverted_priority,"
                    + " deadline, backoff, process_instance_key, root_process_instance_key,"
                    + " element_instance_key, bpmn_process_id, element_id, job_kind, retries,"
                    + " worker FROM CAM_RU_JOB"))
        .containsExactly(
            tuple(
                1L, 0, "test", TENANT_ID, "test", -5L, null, null, 200L, 100L, 201L, "process",
                "task", 1, 2, null));
    final var stored = jobState.getJob(1);
    MsgPackUtil.assertEquality(stored.getVariablesBuffer(), "{}");
    assertThat(stored.getElementInstanceKey()).isEqualTo(201);
  }

  @Test
  void shouldTakeProcessInstanceAsRootOfRecordWithoutRootKey() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    createJob(1, jobRecord(TENANT_ID, 0).setProcessInstanceKey(300));
    transaction.commit();

    // then
    assertThat(
            rows("SELECT job_key, process_instance_key, root_process_instance_key FROM CAM_RU_JOB"))
        .containsExactly(tuple(1L, 300L, 300L));
  }

  @Test
  void shouldLeaveNothingOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    createJob(1, jobRecord(TENANT_ID, 0));
    assertThat(activatableKeys(TENANT_ID)).containsExactly(1L);

    // when
    transaction.rollback();

    // then
    assertThat(jobState.exists(1)).isFalse();
    assertThat(activatableKeys(TENANT_ID)).isEmpty();
    assertThat(rows("SELECT job_key FROM CAM_RU_JOB")).isEmpty();
  }

  @Test
  void shouldSetAndClearLookupColumnsWithTheTransitions() throws Exception {
    // given
    final var record =
        jobRecord(TENANT_ID, 3).setDeadline(1000).setRetryBackoff(10).setRecurringTime(2000);
    createJob(1, record);
    assertThat(lookupColumns()).containsExactly(tuple(0, "test", -3L, null, null));

    // when - then
    jobState.activate(1, record);
    assertThat(lookupColumns()).containsExactly(tuple(1, null, null, 1000L, null));

    failJob(1, record);
    assertThat(lookupColumns()).containsExactly(tuple(2, null, null, null, 2000L));

    recurJob(1, record);
    assertThat(lookupColumns()).containsExactly(tuple(0, "test", -3L, null, null));

    jobState.updateJobState(1, State.SUSPENDED);
    jobState.makeJobNotActivatable(1, record);
    assertThat(lookupColumns()).containsExactly(tuple(6, null, null, null, null));

    jobState.delete(1, record);
    assertThat(lookupColumns()).isEmpty();
  }

  @Test
  void shouldKeepEntryWhenClearedWithAnotherValue() throws Exception {
    // given
    final var record = jobRecord(TENANT_ID, 3).setDeadline(1000);
    createAndActivateJobRecord(1, record);

    // when
    jobState.removeJobDeadline(1, 999);
    jobState.makeJobNotActivatable(1, record);

    // then
    assertThat(jobState.jobDeadlineExists(1, 1000)).isTrue();
    assertThat(lookupColumns()).containsExactly(tuple(1, null, null, 1000L, null));
  }

  @Test
  void shouldRejectASecondEntryOfALookup() {
    // given
    final var record = jobRecord(TENANT_ID, 3).setDeadline(1000);
    createAndActivateJobRecord(1, record);

    // when - then: the same entry may be set again, but no second one
    jobState.makeJobActivatableByPriority(wrapString("test"), 1, TENANT_ID, 3);
    jobState.makeJobActivatableByPriority(wrapString("test"), 1, TENANT_ID, 3);
    assertThatThrownBy(
            () -> jobState.makeJobActivatableByPriority(wrapString("other"), 1, TENANT_ID, 3))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> jobState.makeJobActivatableByPriority(wrapString("test"), 1, TENANT_ID, 4))
        .isInstanceOf(IllegalStateException.class);
    jobState.updateJobDeadline(1, 2000);
    jobState.addJobBackoff(1, 3000);
    assertThatThrownBy(() -> jobState.addJobBackoff(1, 3001))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectLegacyActivation() {
    // when - then
    assertThatThrownBy(() -> jobState.create(1, jobRecord(TENANT_ID, 0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldServeJobsByPriorityThenKeyOfTheGivenTenants() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    createJob(1, jobRecord(TENANT_ID, 0));
    createJob(2, jobRecord(OTHER_TENANT_ID, 5));
    createJob(3, jobRecord(TENANT_ID, -2));
    createJob(4, jobRecord(TENANT_ID, 5));
    createJob(5, jobRecord("third-tenant", 10));
    createJob(6, jobRecord(OTHER_TENANT_ID, Integer.MIN_VALUE));
    createJob(7, jobRecord(TENANT_ID, Integer.MAX_VALUE));
    createJob(8, jobRecord(TENANT_ID, 0).setType("other"));
    transaction.commit();

    // when - then
    assertThat(activatableKeys(TENANT_ID, OTHER_TENANT_ID)).containsExactly(7L, 2L, 4L, 1L, 3L, 6L);
    assertThat(activatableKeys(OTHER_TENANT_ID)).containsExactly(2L, 6L);
    assertThat(activatableKeys()).isEmpty();
  }

  @Test
  void shouldReadActivatableJobsInPagesOnlyWhileTheVisitorContinues() throws Exception {
    mode().assumeSynchronous("counts the queries of a read");
    // given - more jobs than the first page of 16 holds
    final var transaction = transactionContext.getCurrentTransaction();
    for (int key = 1; key <= 40; key++) {
      createJob(key, jobRecord(TENANT_ID, 0));
    }
    transaction.commit();
    transactionContext.getCurrentTransaction();

    // when - then: a visitor that stops in the first page reads one page
    final List<Long> firstThree = new ArrayList<>();
    assertThat(
            queryCounter.queriesOf(
                () -> visitActivatable(1, key -> firstThree.add(key) && firstThree.size() < 3)))
        .containsExactly("CAM_RU_JOB");
    assertThat(firstThree).containsExactly(1L, 2L, 3L);
    // the visited rows are loaded: reading them again needs no query
    assertThat(queryCounter.queriesOf(() -> jobState.getJob(3))).isEmpty();

    // a visitor that takes all jobs reads the pages of 16 and 32
    final List<Long> all = new ArrayList<>();
    assertThat(queryCounter.queriesOf(() -> visitActivatable(1, all::add)))
        .containsExactly("CAM_RU_JOB", "CAM_RU_JOB");
    assertThat(all).hasSize(40).isSorted();
  }

  @Test
  void shouldMergeStagedWritesIntoThePages() throws Exception {
    // given - committed jobs 1 to 40
    final var setup = transactionContext.getCurrentTransaction();
    for (int key = 1; key <= 40; key++) {
      createJob(key, jobRecord(TENANT_ID, 0));
    }
    setup.commit();

    // when - a transaction deletes a job of the first page, moves one of the first page to the
    // end, activates one of the second page and adds jobs before, between and after the pages
    transactionContext.getCurrentTransaction();
    jobState.cancel(2, jobRecord(TENANT_ID, 0));
    jobState.updateJobPriority(3, -1);
    jobState.activate(20, jobRecord(TENANT_ID, 0));
    createJob(100, jobRecord(TENANT_ID, 1));
    createJob(101, jobRecord(TENANT_ID, 0));
    createJob(102, jobRecord(TENANT_ID, -1));

    // then
    final List<Long> expected = new ArrayList<>(List.of(100L, 1L));
    for (long key = 4; key <= 40; key++) {
      if (key != 20) {
        expected.add(key);
      }
    }
    expected.addAll(List.of(101L, 3L, 102L));
    final List<Long> visited = new ArrayList<>();
    visitActivatable(1, visited::add);
    assertThat(visited).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldResumeTimedOutJobsAcrossPages() throws Exception {
    // given - 40 activated jobs with deadlines 1000, 1010, ..., a staged one before all of them
    final var setup = transactionContext.getCurrentTransaction();
    for (int key = 1; key <= 40; key++) {
      createAndActivateJobRecord(key, jobRecord(TENANT_ID, 0).setDeadline(1000 + key * 10L));
    }
    setup.commit();
    transactionContext.getCurrentTransaction();
    createAndActivateJobRecord(50, jobRecord(TENANT_ID, 0).setDeadline(5));

    // when - stop at the 35th
    final List<Long> visited = new ArrayList<>();
    final DeadlineIndex stoppedAt =
        jobState.forEachTimedOutEntry(
            1395, null, (key, record) -> visited.add(key) && visited.size() < 35);

    // then
    assertThat(visited).hasSize(35).startsWith(50L, 1L, 2L);
    assertThat(stoppedAt).isEqualTo(new DeadlineIndex(1340, 34));

    // when - resume at the job it stopped at; jobs 40 and later aren't due
    final List<Long> resumed = new ArrayList<>();
    assertThat(jobState.forEachTimedOutEntry(1395, stoppedAt, (key, record) -> resumed.add(key)))
        .isNull();
    assertThat(resumed).containsExactly(34L, 35L, 36L, 37L, 38L, 39L);
  }

  @Test
  void shouldVisitBackedOffJobsAndReturnTheNextDueDate() throws Exception {
    // given
    final var setup = transactionContext.getCurrentTransaction();
    for (int key = 1; key <= 40; key++) {
      backOff(key, 1000 + key * 10L);
    }
    setup.commit();
    transactionContext.getCurrentTransaction();
    backOff(50, 5);

    // when
    final List<Long> due = new ArrayList<>();
    final long nextDueDate = jobState.findBackedOffJobs(1395, (key, record) -> due.add(key));

    // then
    assertThat(due).hasSize(40).startsWith(50L, 1L).endsWith(39L);
    assertThat(nextDueDate).isEqualTo(1400);

    // when - the visitor stops
    final List<Long> stopped = new ArrayList<>();
    final long stoppedAtDueDate =
        jobState.findBackedOffJobs(
            1395,
            (key, record) -> {
              stopped.add(key);
              return false;
            });
    assertThat(stoppedAtDueDate).isEqualTo(5);
    assertThat(stopped).containsExactly(50L);
  }

  @Test
  void shouldRemoveDeadlinesAndBackoffsThatThePredicateMatches() throws Exception {
    // given
    for (int key = 1; key <= 40; key++) {
      createAndActivateJobRecord(key, jobRecord(TENANT_ID, 0).setDeadline(1000 + key));
    }
    backOff(50, 7);
    backOff(51, 8);

    // when
    jobState.removeInvalidBackoffs(key -> key != 50);
    jobState.cleanupTimeoutsWithoutJobs();

    // then
    assertThat(getBackedOffKeys(Long.MAX_VALUE)).containsExactly(51L);
    assertThat(getTimedOutKeys(Long.MAX_VALUE)).hasSize(40);
  }

  @Test
  void shouldFindJobsOfProcessInstanceAcrossPages() throws Exception {
    // given
    final var setup = transactionContext.getCurrentTransaction();
    for (int key = 1; key <= 40; key++) {
      createJob(key, jobRecord(TENANT_ID, 0).setProcessInstanceKey(key % 2 == 0 ? 10 : 11));
    }
    setup.commit();
    transactionContext.getCurrentTransaction();
    createJob(41, jobRecord(TENANT_ID, 0).setProcessInstanceKey(11));

    // when
    final List<Long> visited = new ArrayList<>();
    jobState.forEachJobsByProcessInstance(11, 5, visited::add);

    // then
    assertThat(visited).hasSize(19).startsWith(5L, 7L).endsWith(39L, 41L);
  }

  private void backOff(final long key, final long recurringTime) {
    final var record =
        jobRecord(TENANT_ID, 0).setRetries(1).setRetryBackoff(1).setRecurringTime(recurringTime);
    createAndActivateJobRecord(key, record);
    failJob(key, record);
  }

  private List<Long> activatableKeys(final String... tenantIds) {
    return getActivatableKeys(wrapString("test"), tenantIds);
  }

  private void visitActivatable(final int limitHint, final LongPredicate visitor) {
    jobState.forEachActivatableJobs(
        wrapString("test"), List.of(TENANT_ID), limitHint, (key, record) -> visitor.test(key));
  }

  private static JobRecord jobRecord(final String tenantId, final int priority) {
    return new JobRecord()
        .setType("test")
        .setRetries(2)
        .setDeadline(256)
        .setTenantId(tenantId)
        .setPriority(priority);
  }

  /**
   * The state and lookup columns of the committed rows; the transitions run without transaction.
   */
  private List<Tuple> lookupColumns() throws SQLException {
    return rows(
        "SELECT state, activatable_type, inverted_priority, deadline, backoff FROM CAM_RU_JOB");
  }

  /**
   * The committed rows of this test's partition, ordered by the first column, of a query of the
   * form {@code SELECT <columns> FROM <table>}.
   */
  private List<Tuple> rows(final String query) throws SQLException {
    final List<Tuple> rows = new ArrayList<>();
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(query + " WHERE partition_id = ? ORDER BY 1")) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        final int columns = resultSet.getMetaData().getColumnCount();
        while (resultSet.next()) {
          final Object[] values = new Object[columns];
          for (int column = 1; column <= columns; column++) {
            values[column - 1] = resultSet.getObject(column);
          }
          rows.add(tuple(values));
        }
      }
    }
    return rows;
  }
}
