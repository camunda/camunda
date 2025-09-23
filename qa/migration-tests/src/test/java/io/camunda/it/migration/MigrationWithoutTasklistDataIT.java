/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.application.Profile;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.it.migration.util.MigrationTestUtils;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class MigrationWithoutTasklistDataIT {

  private static long processDefinitionKey;
  private static final int PROCESS_INSTANCE_COUNT = 40;

  @RegisterExtension
  private static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withPostUpdateAdditionalProfiles(
              Profile.PROCESS_MIGRATION, Profile.USAGE_METRIC_MIGRATION, Profile.TASK_MIGRATION)
          .withBeforeUpgradeConsumer(MigrationWithoutTasklistDataIT::setup);

  @Test
  void allMigrationsHaveRun(final CamundaMigrator migrator) {
    // Process Migration has completed

    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc = MigrationTestUtils.findProcess(migrator, processDefinitionKey);
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isNull();
              assertThat(proc.get().getFormKey()).isNull();
              assertThat(proc.get().getIsPublic()).isFalse();
              assertThat(proc.get().getIsFormEmbedded()).isFalse();
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

    final var metrics =
        migrator
            .getSearchClient()
            .search(
                req ->
                    req.size(100).index(migrator.indexFor(UsageMetricTUTemplate.class).getAlias()),
                UsageMetricsTUEntity.class);

    AssertionsForInterfaceTypes.assertThat(metrics.hits()).isEmpty();
  }

  private static void setup(final DatabaseType db, final CamundaMigrator migrator) {
    MigrationTestUtils.deployForm(migrator, "form/form.form");

    deployAndRunProcesses(migrator);
  }

  private static void deployAndRunProcesses(final CamundaMigrator migrator) {
    final var process =
        Bpmn.createExecutableProcess("task-process")
            .startEvent()
            .name("start")
            .manualTask("manual-task")
            .endEvent()
            .done();

    processDefinitionKey =
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
