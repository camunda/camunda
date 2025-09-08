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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ProcessDefinitionFormDataMigrationIT {

  private static final Map<String, Long> PROCESS_DEFINITION_KEYS = new HashMap<>();

  @RegisterExtension
  private static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withPostUpdateAdditionalProfiles(Profile.PROCESS_MIGRATION)
          .withBeforeUpgradeConsumer(ProcessDefinitionFormDataMigrationIT::setup);

  private static void setup(final DatabaseType databaseType, final CamundaMigrator migrator) {
    MigrationTestUtils.deployForm(migrator, "form/form.form");

    final var formStartedProcessKey =
        MigrationTestUtils.deployProcessDefinition(migrator, "process/process_start_form.bpmn");
    MigrationTestUtils.awaitProcessDefinitionCreated(formStartedProcessKey, migrator);
    PROCESS_DEFINITION_KEYS.put("formStartedProcessKey", formStartedProcessKey);

    final var embeddedFormStartedProcessKey =
        MigrationTestUtils.deployProcessDefinition(migrator, "process/startedByFormProcess.bpmn");
    MigrationTestUtils.awaitProcessDefinitionCreated(embeddedFormStartedProcessKey, migrator);
    PROCESS_DEFINITION_KEYS.put("embeddedFormStartedProcessKey", embeddedFormStartedProcessKey);
  }

  @Test
  void shouldMigrateProcessDefinition(final CamundaMigrator migrator) {

    final var processDefinitionKey = PROCESS_DEFINITION_KEYS.get("formStartedProcessKey");
    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc = MigrationTestUtils.findProcess(migrator, processDefinitionKey);
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isEqualTo("test");
              assertThat(proc.get().getIsPublic()).isFalse();
              assertThat(proc.get().getFormKey()).isNull();
              assertThat(proc.get().getIsFormEmbedded()).isFalse();
            });
  }

  @Test
  void shouldMigrateProcessDefinitionWithEmbeddedForm(final CamundaMigrator migrator) {

    final long processDefinitionKey = PROCESS_DEFINITION_KEYS.get("embeddedFormStartedProcessKey");
    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc = MigrationTestUtils.findProcess(migrator, processDefinitionKey);
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isNull();
              assertThat(proc.get().getFormKey()).isEqualTo("camunda-forms:bpmn:testForm");
              assertThat(proc.get().getIsPublic()).isTrue();
              assertThat(proc.get().getIsFormEmbedded()).isTrue();
            });
  }
}
