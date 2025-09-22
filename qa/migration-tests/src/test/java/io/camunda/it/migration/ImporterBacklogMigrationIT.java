/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.application.Profile;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.it.migration.util.MigrationTestUtils;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ImporterBacklogMigrationIT {
  private static final Map<String, Long> PROCESS_DEFINITION_KEYS = new HashMap<>();
  private static final int PROCESS_INSTANCE_COUNT = 20;

  @RegisterExtension
  private static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withPostUpdateAdditionalProfiles(
              Profile.PROCESS_MIGRATION, Profile.USAGE_METRIC_MIGRATION)
          .withBeforeUpgradeConsumer(ImporterBacklogMigrationIT::setup)
          .withInitialEnvOverrides(
              Map.of(
                  "CAMUNDA_TASKLIST_IMPORTERENABLED",
                  "false",
                  "CAMUNDA_OPERATE_IMPORTERENABLED",
                  "false"));

  @Test
  void allMigrationsHaveRun(final CamundaMigrator migrator) {
    // Process Migration has completed
    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc =
                  MigrationTestUtils.findProcess(
                      migrator, PROCESS_DEFINITION_KEYS.get("formStartedProcessKey"));
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isEqualTo("test");
              assertThat(proc.get().getIsPublic()).isFalse();
              assertThat(proc.get().getFormKey()).isNull();
              assertThat(proc.get().getIsFormEmbedded()).isFalse();
            });

    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc =
                  MigrationTestUtils.findProcess(
                      migrator, PROCESS_DEFINITION_KEYS.get("embeddedFormStartedProcessKey"));
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isNull();
              assertThat(proc.get().getFormKey()).isEqualTo("camunda-forms:bpmn:testForm");
              assertThat(proc.get().getIsPublic()).isTrue();
              assertThat(proc.get().getIsFormEmbedded()).isTrue();
            });

    // Usage Metric Migration has completed
    Awaitility.await("Operate Usage metrics should be present")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var metrics =
                  migrator
                      .getSearchClient()
                      .search(
                          req ->
                              req.size(100)
                                  .index(migrator.indexFor(UsageMetricTemplate.class).getAlias()),
                          UsageMetricsEntity.class);
              AssertionsForInterfaceTypes.assertThat(metrics.hits()).isNotEmpty();
              assertThat(metrics.hits().size()).isGreaterThanOrEqualTo(PROCESS_INSTANCE_COUNT);
            });
  }

  private static void setup(final DatabaseType databaseType, final CamundaMigrator migrator) {
    MigrationTestUtils.deployForm(migrator, "form/form.form");

    final var formStartedProcessKey =
        MigrationTestUtils.deployProcessDefinition(migrator, "process/process_start_form.bpmn");
    PROCESS_DEFINITION_KEYS.put("formStartedProcessKey", formStartedProcessKey);

    final var embeddedFormStartedProcessKey =
        MigrationTestUtils.deployProcessDefinition(migrator, "process/startedByFormProcess.bpmn");
    PROCESS_DEFINITION_KEYS.put("embeddedFormStartedProcessKey", embeddedFormStartedProcessKey);

    try {
      runAndCompleteProcesses(migrator);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static void runAndCompleteProcesses(final CamundaMigrator migrator)
      throws JsonProcessingException {
    final var process =
        Bpmn.createExecutableProcess("task-process")
            .startEvent()
            .name("start")
            .manualTask("manual-task")
            .endEvent()
            .done();

    final var processDefinitionKey =
        migrator
            .getCamundaClient()
            .newDeployResourceCommand()
            .addProcessModel(process, "task-process.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();

    for (int i = 1; i <= PROCESS_INSTANCE_COUNT; i++) {
      migrator
          .getCamundaClient()
          .newCreateInstanceCommand()
          .processDefinitionKey(processDefinitionKey)
          .send()
          .join();
    }
  }
}
