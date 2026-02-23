/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.AuditLogFixtures;
import io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class HistoryCleanupIT {

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired RdbmsService rdbmsService;

  HistoryCleanupService historyCleanupService;

  RdbmsWriters rdbmsWriters;

  @BeforeEach
  void setUp() {
    final var config = new RdbmsWriterConfig.Builder().partitionId(0).build();
    rdbmsWriters = rdbmsService.createWriter(config);
    historyCleanupService =
        new HistoryCleanupService(config, rdbmsWriters, rdbmsService.getProcessInstanceReader());
  }

  @Test
  public void shouldUpdateHistoryCleanupDateForRootProcessInstance() {
    // GIVEN
    final Long rootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    final Long processInstanceKey =
        createRandomProcessInstance(
            b ->
                b.processInstanceKey(rootProcessInstanceKey)
                    .rootProcessInstanceKey(rootProcessInstanceKey));

    // WHEN we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleProcessForHistoryCleanup(processInstanceKey, now);
    rdbmsWriters.flush();

    // THEN
    final var expectedDate = now.plus(historyCleanupService.getHistoryCleanupInterval());

    final var historyCleanupDate1 =
        jdbcTemplate.queryForObject(
            "SELECT HISTORY_CLEANUP_DATE FROM "
                + "PROCESS_INSTANCE"
                + " WHERE PROCESS_INSTANCE_KEY = "
                + processInstanceKey,
            OffsetDateTime.class);

    assertThat(historyCleanupDate1)
        .describedAs("should update the history cleanup date for PROCESS_INSTANCE but date is null")
        .isNotNull();

    assertThat(historyCleanupDate1)
        .describedAs(
            "should update the history cleanup date for PROCESS_INSTANCE but date is wrong")
        .isCloseTo(expectedDate, within(10, ChronoUnit.MILLIS));
  }

  @Test
  public void shouldNotUpdateHistoryCleanupDateForNonRootProcessInstance() {
    // GIVEN
    final Long processInstanceKey = createRandomProcessInstance();

    // WHEN we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleProcessForHistoryCleanup(processInstanceKey, now);
    rdbmsWriters.flush();

    // THEN
    final var historyCleanupDate =
        jdbcTemplate.queryForObject(
            "SELECT HISTORY_CLEANUP_DATE FROM "
                + "PROCESS_INSTANCE"
                + " WHERE PROCESS_INSTANCE_KEY = "
                + processInstanceKey,
            OffsetDateTime.class);

    assertThat(historyCleanupDate)
        .describedAs(
            "should not update the history cleanup date for PROCESS_INSTANCE but date was not null")
        .isNull();
  }

  @Test
  public void shouldUpdateHistoryCleanupDateForBatchOperation() {
    // GIVEN
    final var batchOperation =
        BatchOperationFixtures.createAndSaveBatchOperation(rdbmsWriters, b -> b);
    final var batchOperationKey = batchOperation.batchOperationKey();
    final BatchOperationType batchOperationType = batchOperation.operationType();

    BatchOperationFixtures.createAndSaveRandomBatchOperationItems(
        rdbmsWriters, batchOperationKey, 5);

    // WHEN we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleBatchOperationForHistoryCleanup(
        batchOperationKey, batchOperationType, now);
    rdbmsWriters.flush();

    // THEN
    final var expectedDate =
        now.plus(historyCleanupService.resolveBatchOperationTTL(batchOperationType));
    assertThat(getBatchOperationHistoryCleanupDate(batchOperationKey))
        .describedAs("should update the history cleanup date for batch operation")
        .isNotNull()
        .isCloseTo(expectedDate, within(10, ChronoUnit.MILLIS));
  }

  private Long createRandomProcessInstance() {
    return createRandomProcessInstance(b -> b);
  }

  private Long createRandomProcessInstance(
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    final ProcessInstanceDbModel processInstance =
        ProcessInstanceFixtures.createAndSaveRandomProcessInstance(rdbmsWriters, builderFunction);
    return processInstance.processInstanceKey();
  }

  private OffsetDateTime getBatchOperationHistoryCleanupDate(final String batchOperationKey) {
    return jdbcTemplate.queryForObject(
        "SELECT HISTORY_CLEANUP_DATE FROM BATCH_OPERATION "
            + "WHERE BATCH_OPERATION_KEY = '"
            + batchOperationKey
            + "'",
        OffsetDateTime.class);
  }

  @Test
  public void shouldSetHistoryCleanupDateForDecisionInstanceWithoutProcessInstance() {
    // GIVEN
    // Create a decision instance without a processInstanceKey (using -1)
    // Use a deterministic evaluation date for predictable cleanup date calculation
    final var evaluationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    final var decisionInstance =
        DecisionInstanceFixtures.createAndSaveRandomDecisionInstance(
            rdbmsWriters,
            b -> b.processInstanceKey(-1L).evaluationDate(evaluationDate).historyCleanupDate(null));
    rdbmsWriters.flush();

    // THEN - verify cleanup date is calculated correctly
    final OffsetDateTime cleanupDate =
        jdbcTemplate.queryForObject(
            "SELECT HISTORY_CLEANUP_DATE FROM DECISION_INSTANCE "
                + "WHERE DECISION_INSTANCE_KEY = "
                + decisionInstance.decisionInstanceKey(),
            OffsetDateTime.class);

    // The cleanup date should be evaluationDate + decisionInstanceTTL (default 30 days)
    final var expectedCleanupDate = evaluationDate.plusDays(30);
    assertThat(cleanupDate)
        .describedAs(
            "should have cleanup date set to evaluationDate + decisionInstanceTTL for decision"
                + " instance without process instance")
        .isNotNull()
        .isEqualTo(expectedCleanupDate);
  }

  @Test
  public void shouldDeleteStandaloneDecisionInstanceDuringCleanup() {
    // GIVEN - Create a standalone decision instance with an expired cleanup date
    final var evaluationDate = OffsetDateTime.now().minusDays(40);
    final var decisionInstance =
        DecisionInstanceFixtures.createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processInstanceKey(-1L).evaluationDate(evaluationDate));
    rdbmsWriters.flush();

    // Verify it was created
    final var countBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM DECISION_INSTANCE WHERE DECISION_INSTANCE_KEY = "
                + decisionInstance.decisionInstanceKey(),
            Integer.class);
    assertThat(countBefore).isEqualTo(1);

    // WHEN - Run cleanup with a date that should trigger deletion
    final var cleanupDate = OffsetDateTime.now();
    historyCleanupService.cleanupHistory(0, cleanupDate);
    rdbmsWriters.flush();

    // THEN - Verify the standalone decision instance was deleted
    final var countAfter =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM DECISION_INSTANCE WHERE DECISION_INSTANCE_KEY = "
                + decisionInstance.decisionInstanceKey(),
            Integer.class);
    assertThat(countAfter)
        .describedAs("Standalone decision instance should be deleted during cleanup")
        .isEqualTo(0);
  }

  @Test
  public void shouldDeleteStandaloneDecisionAuditLogDuringCleanup() {
    // GIVEN - Create a standalone decision audit log with an expired cleanup date
    final var evaluationDate = OffsetDateTime.now().minusDays(40);
    final var processInstanceKey = Math.abs(new java.util.Random().nextLong());
    final var auditLog =
        AuditLogFixtures.createRandomized(
            b ->
                b.entityType(AuditLogEntityType.DECISION)
                    .processInstanceKey(processInstanceKey)
                    .historyCleanupDate(evaluationDate.plusDays(5))
                    .timestamp(evaluationDate));
    AuditLogFixtures.createAndSaveAuditLog(rdbmsWriters, auditLog);
    rdbmsWriters.flush();

    // Verify it was created
    final var countBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM AUDIT_LOG WHERE AUDIT_LOG_KEY = '" + auditLog.auditLogKey() + "'",
            Integer.class);
    assertThat(countBefore).isEqualTo(1);

    // WHEN - Run cleanup with a date that should trigger deletion
    final var cleanupDate = OffsetDateTime.now();
    historyCleanupService.cleanupHistory(0, cleanupDate);
    rdbmsWriters.flush();

    // THEN - Verify the standalone decision audit log was deleted
    final var countAfter =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM AUDIT_LOG WHERE AUDIT_LOG_KEY = '" + auditLog.auditLogKey() + "'",
            Integer.class);
    assertThat(countAfter)
        .describedAs("Standalone decision audit log should be deleted during cleanup")
        .isEqualTo(0);
  }

  @Test
  public void shouldNotDeleteDecisionInstancesWithProcessInstanceDuringStandaloneCleanup() {
    // GIVEN - Create a decision instance WITH a process instance
    final var processInstance =
        ProcessInstanceFixtures.createAndSaveRandomProcessInstance(rdbmsWriters, b -> b);
    final var evaluationDate = OffsetDateTime.now().minusDays(40);
    final var decisionInstance =
        DecisionInstanceFixtures.createAndSaveRandomDecisionInstance(
            rdbmsWriters,
            b ->
                b.processInstanceKey(processInstance.processInstanceKey())
                    .evaluationDate(evaluationDate));
    rdbmsWriters.flush();

    // Verify it was created
    final var countBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM DECISION_INSTANCE WHERE DECISION_INSTANCE_KEY = "
                + decisionInstance.decisionInstanceKey(),
            Integer.class);
    assertThat(countBefore).isEqualTo(1);

    // WHEN - Run cleanup with a date that should trigger deletion
    final var cleanupDate = OffsetDateTime.now();
    historyCleanupService.cleanupHistory(0, cleanupDate);
    rdbmsWriters.flush();

    // THEN - Verify the decision instance with PI was NOT deleted by standalone cleanup
    final var countAfter =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM DECISION_INSTANCE WHERE DECISION_INSTANCE_KEY = "
                + decisionInstance.decisionInstanceKey(),
            Integer.class);
    assertThat(countAfter)
        .describedAs(
            "Decision instance with process instance should NOT be deleted by standalone cleanup")
        .isEqualTo(1);
  }
}
