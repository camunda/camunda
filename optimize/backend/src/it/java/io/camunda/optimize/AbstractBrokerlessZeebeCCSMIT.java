/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for CCSM integration tests that seed Zeebe records directly into ES indices without
 * requiring a live Zeebe broker. Use this instead of {@link AbstractCCSMIT} when the test verifies
 * import pipeline or script logic by writing fake records directly to the Zeebe export index.
 */
@Tag("ccsm-test")
@ActiveProfiles(CCSM_PROFILE)
public abstract class AbstractBrokerlessZeebeCCSMIT extends AbstractIT {

  protected static final String ZEEBE_RECORD_PREFIX =
      ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX + "-" + IdGenerator.getNextId();

  /**
   * Defensive pre-test cleanup: removes any Zeebe records that may have been left behind if the
   * previous test's {@code @AfterEach} did not run (e.g. after a JVM crash or a failed teardown).
   * Under normal conditions the {@link #cleanupZeebeRecords()} after-hook already ensures a clean
   * slate, so this is a no-op for well-behaved tests.
   */
  @BeforeEach
  @Order(1)
  public void ensureCleanZeebeState() {
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(ZEEBE_RECORD_PREFIX);
  }

  @BeforeEach
  @Order(2)
  public void configureZeebeImport() {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setName(ZEEBE_RECORD_PREFIX);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @AfterEach
  @Order(1)
  public void cleanupZeebeRecords() {
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(ZEEBE_RECORD_PREFIX);
  }

  /**
   * Clears Optimize-side data (process instance docs, etc.) between tests so subclasses sharing
   * fixed instance IDs do not leak documents from one test into the next. Mirrors the cleanup that
   * {@link AbstractCCSMIT} performs for live-broker tests.
   */
  @AfterEach
  @Order(2)
  public void cleanupOptimizeData() {
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  /**
   * Tears down the per-class Zeebe indices created during this suite. Runs after all test methods
   * to remove the indices themselves (not just the documents), avoiding leftover indices in the
   * cluster between test runs.
   */
  @AfterAll
  static void deleteZeebeIndices() {
    databaseIntegrationTestExtension.deleteAllZeebeIndicesForPrefix(ZEEBE_RECORD_PREFIX);
  }

  @Override
  protected void startAndUseNewOptimizeInstance() {
    startAndUseNewOptimizeInstance(new HashMap<>(), CCSM_PROFILE);
  }

  protected void importAllZeebeEntitiesFromScratch() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromScratch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  /**
   * Runs one import round from the current import positions, without resetting them. Use this after
   * {@link #importAllZeebeEntitiesFromScratch()} to simulate records arriving in a later broker
   * export batch, exercising the cross-batch Painless merge path.
   */
  protected void importAllZeebeEntitiesFromLastIndex() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromLastIndex();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  /**
   * Persists the given process instances directly into the Optimize process-instance index, then
   * refreshes all indices so subsequent reads in the same test see the data immediately. Use this
   * when you want to test report logic without going through the Zeebe import pipeline.
   *
   * <p>Also persists a minimal process definition for each unique definition key+version in the
   * list, so report evaluation via allFullyImportedProcessDefinitions() can find them.
   */
  protected void persistProcessInstances(final List<ProcessInstanceDto> instances) {
    final List<ProcessDefinitionOptimizeDto> definitions =
        instances.stream()
            .filter(i -> i.getProcessDefinitionKey() != null)
            .map(
                i ->
                    ProcessDefinitionOptimizeDto.builder()
                        .id(i.getProcessDefinitionId())
                        .key(i.getProcessDefinitionKey())
                        .version(i.getProcessDefinitionVersion())
                        .name(i.getProcessDefinitionKey())
                        .dataSource(i.getDataSource())
                        .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                        .bpmn20Xml("<definitions/>")
                        .build())
            .distinct()
            .toList();
    embeddedOptimizeExtension
        .getBean(ProcessDefinitionWriter.class)
        .importProcessDefinitions(definitions);

    final ProcessInstanceWriter writer =
        embeddedOptimizeExtension.getBean(ProcessInstanceWriter.class);
    final DatabaseClient dbClient = embeddedOptimizeExtension.getBean(DatabaseClient.class);
    final var requests = writer.generateProcessInstanceImports(instances, "test-source-index");
    dbClient.executeImportRequestsAsBulk(
        "test process instances",
        requests,
        embeddedOptimizeExtension
            .getConfigurationService()
            .getSkipDataAfterNestedDocLimitReached());
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  /** Returns a builder for a minimal completed {@link ProcessInstanceDto}. */
  protected static ProcessInstanceDto.ProcessInstanceDtoBuilder completedInstance(
      final String processDefinitionKey) {
    return ProcessInstanceDto.builder()
        .processInstanceId(UUID.randomUUID().toString())
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionVersion("1")
        .processDefinitionId(processDefinitionKey + ":1:1")
        .tenantId(ZEEBE_DEFAULT_TENANT_ID)
        .state(ProcessInstanceConstants.COMPLETED_STATE)
        .dataSource(new ZeebeDataSourceDto("test-source", 1))
        .startDate(OffsetDateTime.now().minusHours(2))
        .endDate(OffsetDateTime.now().minusHours(1))
        .duration(3_600_000L);
  }
}
