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
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
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
@TestPropertySource(properties = {"spring.liquibase.enabled=false", "camunda.database.type=rdbms"})
public class HistoryCleanupIT {

  private static final List<String> TABLE_NAMES =
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

  RdbmsWriter rdbmsWriter;

  @BeforeEach
  void setUp() {
    rdbmsWriter = rdbmsService.createWriter(0);
    historyCleanupService = rdbmsWriter.getHistoryCleanupService();
  }

  @Test
  public void shouldUpdateHistoryCleanupDate() {
    // GIVEN
    final Long processInstanceKey = createRandomProcessWithCleanupRelevantData();

    // WHEN we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleProcessForHistoryCleanup(processInstanceKey, now);
    rdbmsWriter.flush();

    // THEN
    final var expectedDate = now.plus(historyCleanupService.getHistoryCleanupInterval());
    getHistoryCleanupDates(processInstanceKey)
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

  private Long createRandomProcessWithCleanupRelevantData() {
    final Long processInstanceKey =
        ProcessInstanceFixtures.createAndSaveRandomProcessInstance(
                rdbmsService.createWriter(0), b -> b)
            .processInstanceKey();

    FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances(
        rdbmsWriter, b -> b.processInstanceKey(processInstanceKey));

    UserTaskFixtures.createAndSaveRandomUserTasks(
        rdbmsService, b -> b.processInstanceKey(processInstanceKey));

    VariableFixtures.createAndSaveRandomVariables(
        rdbmsService, b -> b.processInstanceKey(processInstanceKey));

    IncidentFixtures.createAndSaveRandomIncidents(
        rdbmsWriter, b -> b.processInstanceKey(processInstanceKey));

    DecisionInstanceFixtures.createAndSaveRandomDecisionInstances(
        rdbmsWriter, b -> b.processInstanceKey(processInstanceKey));

    return processInstanceKey;
  }

  private Map<String, List<Object>> getHistoryCleanupDates(final Long processInstanceKey) {
    final Map<String, List<Object>> historyCleanupDatesMap = new HashMap<>();

    TABLE_NAMES.forEach(
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
}
