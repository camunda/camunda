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
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ElementInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
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

  private static final List<String> PROCESS_RELATED_TABLE_NAMES =
      List.of(
          "VARIABLE",
          "FLOW_NODE_INSTANCE",
          "PROCESS_INSTANCE",
          "USER_TASK",
          "INCIDENT",
          "DECISION_INSTANCE");

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired RdbmsService rdbmsService;

  HistoryCleanupService historyCleanupService;

  RdbmsWriters rdbmsWriters;

  @BeforeEach
  void setUp() {
    final var config = new RdbmsWriterConfig.Builder().partitionId(0).build();
    rdbmsWriters = rdbmsService.createWriter(config);
    historyCleanupService = new HistoryCleanupService(config, rdbmsWriters);
  }

  @Test
  public void shouldUpdateHistoryCleanupDateForProcess() {
    // GIVEN
    final Long processInstanceKey = createRandomProcessWithCleanupRelevantData();

    // WHEN we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleProcessForHistoryCleanup(processInstanceKey, now);
    rdbmsWriters.flush();

    // THEN
    final var expectedDate = now.plus(historyCleanupService.getHistoryCleanupInterval());
    getProcessRelatedHistoryCleanupDates(processInstanceKey)
        .forEach(
            (tableName, historyCleanupDate) -> {
              historyCleanupDate.forEach(
                  date -> {
                    assertThat(date)
                        .describedAs(
                            "should update the history cleanup date for %s but date is null",
                            tableName)
                        .isNotNull();
                    assertThat((OffsetDateTime) date)
                        .describedAs(
                            "should update the history cleanup date for %s but date is wrong",
                            tableName)
                        .isCloseTo(expectedDate, within(10, ChronoUnit.MILLIS));
                  });
            });
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

  private Long createRandomProcessWithCleanupRelevantData() {
    final Long processInstanceKey =
        ProcessInstanceFixtures.createAndSaveRandomProcessInstance(rdbmsWriters, b -> b)
            .processInstanceKey();

    ElementInstanceFixtures.createAndSaveRandomElementInstances(
        rdbmsWriters, b -> b.processInstanceKey(processInstanceKey));

    UserTaskFixtures.createAndSaveRandomUserTasks(
        rdbmsService, b -> b.processInstanceKey(processInstanceKey));

    VariableFixtures.createAndSaveRandomVariables(
        rdbmsService, b -> b.processInstanceKey(processInstanceKey));

    IncidentFixtures.createAndSaveRandomIncidents(
        rdbmsWriters, b -> b.processInstanceKey(processInstanceKey));

    DecisionInstanceFixtures.createAndSaveRandomDecisionInstances(
        rdbmsWriters, b -> b.processInstanceKey(processInstanceKey));

    return processInstanceKey;
  }

  private Map<String, List<Object>> getProcessRelatedHistoryCleanupDates(
      final Long processInstanceKey) {
    final Map<String, List<Object>> historyCleanupDatesMap = new HashMap<>();

    PROCESS_RELATED_TABLE_NAMES.forEach(
        tableName -> {
          final List<Object> historyCleanupDates =
              jdbcTemplate
                  .queryForList(
                      "SELECT HISTORY_CLEANUP_DATE FROM "
                          + tableName
                          + " WHERE PROCESS_INSTANCE_KEY = "
                          + processInstanceKey)
                  .stream()
                  .map(row -> row.get("HISTORY_CLEANUP_DATE"))
                  .collect(Collectors.toList());
          historyCleanupDatesMap.put(tableName, historyCleanupDates);
        });

    return historyCleanupDatesMap;
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
}
