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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.deployment.AbstractProcessStateContractTest;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.deployment.ProcessStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the process state contract against PostgreSQL, plus the tests for what is specific to the
 * relational store: rows are written on commit only, rolled back writes leave neither rows nor
 * cached entries, and a new state on the same partition, e.g. after a restart, finds everything in
 * the database.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition to stay
 * isolated.
 */
abstract class AbstractRdbmsProcessStateTest extends AbstractProcessStateContractTest {

  /** How the session under test writes: directly, or behind the commit. */
  abstract StateMode mode();

  private static EngineStateDatabase database;

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
  protected MutableProcessState createProcessState() {
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    return newProcessState(transactionContext);
  }

  @Override
  protected TransactionContext transactionContext() {
    return transactionContext;
  }

  @AfterEach
  void endTransaction() throws Exception {
    if (transactionContext != null) {
      transactionContext.getCurrentTransaction().rollback();
    }
  }

  @Test
  void shouldWriteRowsToDatabaseOnlyOnCommit() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    final var processRecord = creatingProcessRecord("processId", 1, "v1");
    processState.putProcess(processRecord.getKey(), processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    processState.addPendingDeletion(processRecord.getKey(), 2);
    processState.markProcessDefinitionRestoreAsRun();
    assertThat(committedRowCounts()).allSatisfy((table, count) -> assertThat(count).isZero());

    // when
    transaction.commit();

    // then
    assertThat(committedRowCounts())
        .containsOnlyKeys(RdbmsEngineStateSchema.TABLES)
        .allSatisfy(
            (table, count) ->
                assertThat(count)
                    .describedAs(table)
                    .isEqualTo(
                        RdbmsEngineStateSchema.PROCESS_INSTANCE_TABLES.contains(table)
                                || RdbmsEngineStateSchema.METRIC_TABLES.contains(table)
                            ? 0
                            : 1));
  }

  @Test
  void shouldLeaveNoRowsAndNoCachedProcessOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    final var processRecord = creatingProcessRecord("processId", 1, "v1");
    processState.putProcess(processRecord.getKey(), processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    assertThat(processState.getProcessByKeyAndTenant(processRecord.getKey(), TENANT_ID))
        .isNotNull();
    assertThat(processState.getLatestProcessVersion("processId", TENANT_ID)).isOne();

    // when
    transaction.rollback();

    // then
    assertThat(committedRowCounts()).allSatisfy((table, count) -> assertThat(count).isZero());
    assertThat(processState.getProcessByKeyAndTenant(processRecord.getKey(), TENANT_ID)).isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                processRecord.getBpmnProcessIdBuffer(), 1, TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                processRecord.getBpmnProcessIdBuffer(),
                processRecord.getDeploymentKey(),
                TENANT_ID))
        .isNull();
    assertThat(processState.getLatestVersionDigest(wrapString("processId"), TENANT_ID)).isNull();
  }

  @Test
  void shouldNotKeepVersionNumbersOfRolledBackTransaction() throws Exception {
    // given
    final var version1 = creatingProcessRecord("processId", 1);
    processState.putProcess(version1.getKey(), version1);
    final var transaction = transactionContext.getCurrentTransaction();
    final var version2 = creatingProcessRecord("processId", 2);
    processState.putProcess(version2.getKey(), version2);

    // when
    transaction.rollback();

    // then - the next deployment gets version 2 again, like after a restart
    assertThat(processState.getLatestProcessVersion("processId", TENANT_ID)).isOne();
    assertThat(processState.getNextProcessVersion("processId", TENANT_ID)).isEqualTo(2);
    assertThat(processState.getKnownProcessVersions("processId", TENANT_ID)).containsExactly(1L);
  }

  @Test
  void shouldLoadEverythingFromDatabaseInNewState() {
    // given - committed processes, read by the state so they are cached
    final var version1 = creatingProcessRecord("processId", 1, "v1");
    final var version2 = creatingProcessRecord("processId", 2, "v1");
    for (final var processRecord : List.of(version1, version2)) {
      processState.putProcess(processRecord.getKey(), processRecord);
      processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(processRecord);
      processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    }
    processState.markDraining(version2);
    processState.addPendingDeletion(version2.getKey(), 3);
    processState.markProcessDefinitionRestoreAsRun();

    // when - a new state on the same partition, e.g. after a restart, starts with empty caches
    final var restartedState = newProcessState(new JdbcTransactionContext());

    // then
    final var processId = wrapString("processId");
    assertThat(restartedState.getProcessByKeyAndTenant(version1.getKey(), TENANT_ID).getVersion())
        .isOne();
    assertThat(restartedState.getLatestProcessVersionByProcessId(processId, TENANT_ID).getKey())
        .isEqualTo(version2.getKey());
    assertThat(restartedState.getLatestProcessVersionByProcessId(processId, TENANT_ID).getState())
        .isEqualTo(PersistedProcessState.DRAINING);
    assertThat(
            restartedState.getLatestActiveProcessVersionByProcessId(processId, TENANT_ID).getKey())
        .isEqualTo(version1.getKey());
    assertThat(
            restartedState
                .getProcessByProcessIdAndDeploymentKey(
                    processId, version1.getDeploymentKey(), TENANT_ID)
                .getKey())
        .isEqualTo(version1.getKey());
    assertThat(
            restartedState.getProcessByProcessIdAndVersionTag(processId, "v1", TENANT_ID).getKey())
        .isEqualTo(version2.getKey());
    assertThat(restartedState.getLatestVersionDigest(processId, TENANT_ID))
        .isEqualTo(version2.getChecksumBuffer());
    assertThat(restartedState.getNextProcessVersion("processId", TENANT_ID)).isEqualTo(3);
    assertThat(restartedState.getKnownProcessVersions("processId", TENANT_ID))
        .containsExactly(1L, 2L);
    assertThat(restartedState.getPendingDeletionPartitions(version2.getKey())).containsExactly(3);
    assertThat(restartedState.hasRunProcessDefinitionRestore()).isTrue();
  }

  @Test
  void shouldStoreLookupColumnsOfProcess() throws SQLException {
    // given
    final var tagged = creatingProcessRecord("processId", 1, "v1");
    final var untagged = creatingProcessRecord("processId", 2).setDeploymentKey(-1);

    // when
    processState.putProcess(tagged.getKey(), tagged);
    processState.putProcess(untagged.getKey(), untagged);
    processState.markDraining(tagged);

    // then
    assertThat(processRow(tagged.getKey()))
        .containsExactly(
            entry("tenant_id", TENANT_ID),
            entry("bpmn_process_id", "processId"),
            entry("version", "1"),
            entry("version_tag", "v1"),
            entry("deployment_key", String.valueOf(tagged.getDeploymentKey())),
            entry("resource_name", "process.bpmn"),
            entry("state", String.valueOf(PersistedProcessState.DRAINING.ordinal())));
    assertThat(processRow(untagged.getKey()))
        .containsEntry("version_tag", null)
        .containsEntry("deployment_key", "-1")
        .containsEntry("state", String.valueOf(PersistedProcessState.ACTIVE.ordinal()));
    assertThat(
            database.countRows(
                "CAM_RU_PROCESS_VERSION",
                partitionId,
                "bpmn_process_id = 'processId' AND highest_version = 2 AND latest_version = 2"))
        .isOne();
  }

  @Test
  void shouldNotHaveDeploymentLookupForProcessPutWithoutIt() throws Exception {
    // given - like a process created by an applier that doesn't register the deployment mapping
    final var processRecord = creatingProcessRecord("processId", 1, null);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    transactionContext.getCurrentTransaction().commit();

    // then
    assertThat(
            database.countRows(
                "CAM_RU_PROCESS_DEFINITION",
                partitionId,
                "deployment_key_lookup IS NULL AND deployment_key = "
                    + processRecord.getDeploymentKey()))
        .isOne();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                wrapString("processId"), processRecord.getDeploymentKey(), TENANT_ID))
        .isNull();
  }

  @Test
  void shouldFindNoProcessByVersionTagAfterDeletingOlderVersionWithSameTagWithoutRepointing()
      throws Exception {
    // given - the tag lookup points at v2, v1 carries the same tag
    final var v1 = creatingProcessRecord("processId", 1, "shared");
    final var v2 = creatingProcessRecord("processId", 2, "shared");
    for (final var processRecord : List.of(v1, v2)) {
      processState.putProcess(processRecord.getKey(), processRecord);
      processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    }
    transactionContext.getCurrentTransaction().commit();

    // when - the delete of the Process:DELETED v1 applier drops the tag's lookup
    processState.deleteProcess(v1);
    transactionContext.getCurrentTransaction().commit();

    // then
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                wrapString("processId"), "shared", TENANT_ID))
        .isNull();
    assertThat(processState.getProcessByKeyAndTenant(v2.getKey(), TENANT_ID)).isNotNull();
    assertThat(
            database.countRows(
                "CAM_RU_PROCESS_DEFINITION", partitionId, "version_tag_lookup IS NOT NULL"))
        .isZero();
  }

  @Test
  void shouldMoveVersionTagLookupToPreviousVersionWhenLatestIsDeletedInLaterTransaction()
      throws Exception {
    // given
    final var v1 = creatingProcessRecord("processId", 1, "shared");
    final var v2 = creatingProcessRecord("processId", 2, "shared");
    for (final var processRecord : List.of(v1, v2)) {
      processState.putProcess(processRecord.getKey(), processRecord);
      processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    }
    transactionContext.getCurrentTransaction().commit();

    // when
    processState.deleteProcessAndRepointVersionTag(v2);
    transactionContext.getCurrentTransaction().commit();

    // then
    assertThat(
            database.countRows(
                "CAM_RU_PROCESS_DEFINITION",
                partitionId,
                "version_tag_lookup = 'shared' AND process_definition_key = " + v1.getKey()))
        .isOne();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                wrapString("processId"), "shared", TENANT_ID))
        .extracting(deployed -> deployed.getKey())
        .isEqualTo(v1.getKey());
  }

  @Test
  void shouldKeepVersionInfoWithoutDigestAfterDeletingTheLatestVersionTwice() throws Exception {
    // given
    final var processRecord = creatingProcessRecord("processId", 1, null);
    processState.putProcess(processRecord.getKey(), processRecord);
    transactionContext.getCurrentTransaction().commit();
    assertThat(processState.getLatestVersionDigest(wrapString("processId"), TENANT_ID)).isNotNull();

    // when
    processState.deleteProcess(processRecord);
    transactionContext.getCurrentTransaction().commit();

    // then - the version info remains, so versions aren't reused, but it has no digest
    assertThat(processState.getLatestVersionDigest(wrapString("processId"), TENANT_ID)).isNull();
    assertThat(processState.getNextProcessVersion("processId", TENANT_ID)).isEqualTo(2);
    assertThat(
            database.countRows(
                "CAM_RU_PROCESS_VERSION",
                partitionId,
                "bpmn_process_id = 'processId' AND digest IS NULL"))
        .isOne();
  }

  @Test
  void shouldStoreProcessDefinitionRestoreAsRowOfPartitionState() throws Exception {
    // when
    processState.markProcessDefinitionRestoreAsRun();
    transactionContext.getCurrentTransaction().commit();

    // then
    assertThat(
            database.countRows(
                "CAM_RU_PARTITION_STATE",
                partitionId,
                "state_key = '" + RdbmsProcessStore.PROCESS_DEFINITION_RESTORE_KEY + "'"))
        .isOne();
    assertThat(processState.hasRunProcessDefinitionRestore()).isTrue();
  }

  private ProcessStateImpl newProcessState(final JdbcTransactionContext context) {
    return new ProcessStateImpl(
        new RdbmsProcessStore(database.entitySession(mode(), context, partitionId), context),
        new EngineConfiguration(),
        newTransformer(),
        context,
        new SimpleMeterRegistry());
  }

  private Map<String, Long> committedRowCounts() {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final String table : RdbmsEngineStateSchema.TABLES) {
      counts.put(table, database.countRows(table, partitionId, "1 = 1"));
    }
    return counts;
  }

  /** The lookup columns of the committed process row, read on a fresh connection. */
  private Map<String, @Nullable String> processRow(final long processDefinitionKey)
      throws SQLException {
    try (final var freshConnection = database.dataSource().getConnection();
        final var statement =
            freshConnection.prepareStatement(
                "SELECT tenant_id, bpmn_process_id, version, version_tag, deployment_key,"
                    + " resource_name, state FROM CAM_RU_PROCESS_DEFINITION"
                    + " WHERE partition_id = ? AND process_definition_key = ?")) {
      statement.setInt(1, partitionId);
      statement.setLong(2, processDefinitionKey);
      try (final ResultSet resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).describedAs("process row exists").isTrue();
        final Map<String, @Nullable String> row = new LinkedHashMap<>();
        final var metaData = resultSet.getMetaData();
        for (int column = 1; column <= metaData.getColumnCount(); column++) {
          row.put(metaData.getColumnName(column), resultSet.getString(column));
        }
        return row;
      }
    }
  }
}
