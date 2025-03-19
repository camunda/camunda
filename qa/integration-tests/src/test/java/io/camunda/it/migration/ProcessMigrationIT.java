/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class ProcessMigrationIT {

  private static final Map<String, Long> processDefinitionKeys = new HashMap<>();

  @RegisterExtension
  static final MigrationITExtension PROVIDER =
      new MigrationITExtension().withBeforeUpgradeConsumer(ProcessMigrationIT::setup);

  private static void setup(final DatabaseType databaseType, final CamundaMigrator migrator) {
    migrator
        .getCamundaClient()
        .newDeployResourceCommand()
        .addResourceFromClasspath("form/form.form")
        .send()
        .join();
    final var formStartedProcessKey =
        migrator
            .getCamundaClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/process_start_form.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    awaitProcessDefinitionCreated(formStartedProcessKey, migrator);
    processDefinitionKeys.put("formStartedProcessKey", formStartedProcessKey);

    final var embeddedFormStartedProcessKey =
        migrator
            .getCamundaClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/startedByFormProcess.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    awaitProcessDefinitionCreated(embeddedFormStartedProcessKey, migrator);

    processDefinitionKeys.put("embeddedFormStartedProcessKey", embeddedFormStartedProcessKey);
  }

  @Test
  void shouldMigrateProcessDefinition(final CamundaMigrator migrator) {

    final var processDefinitionKey = processDefinitionKeys.get("formStartedProcessKey");
    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc = findProcess(migrator, processDefinitionKey);
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isEqualTo("test");
              assertThat(proc.get().getIsPublic()).isFalse();
              assertThat(proc.get().getFormKey()).isNull();
              assertThat(proc.get().getIsFormEmbedded()).isFalse();
            });
  }

  @Test
  void shouldMigrateProcessDefinitionWithEmbeddedForm(final CamundaMigrator migrator) {

    final long processDefinitionKey = processDefinitionKeys.get("embeddedFormStartedProcessKey");

    Awaitility.await("Form data should be present on process definition")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var proc = findProcess(migrator, processDefinitionKey);
              assertThat(proc).isPresent();
              assertThat(proc.get().getFormId()).isNull();
              assertThat(proc.get().getFormKey()).isEqualTo("camunda-forms:bpmn:testForm");
              assertThat(proc.get().getIsPublic()).isTrue();
              assertThat(proc.get().getIsFormEmbedded()).isTrue();
            });
  }

  private static void awaitProcessDefinitionCreated(
      final long processDefinitionKey, final CamundaMigrator migrator) {
    final ObjectMapper mapper = new ObjectMapper();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () -> {
              final var res =
                  migrator.request(
                      b ->
                          b.GET()
                              .uri(
                                  URI.create(
                                      migrator.getWebappsUrl()
                                          + "/process-definitions/"
                                          + processDefinitionKey)),
                      HttpResponse.BodyHandlers.ofString());

              if (res.statusCode() == 200) {
                final var proc = mapper.readValue(res.body(), ProcessDefinition.class);
                return proc.getKey() == processDefinitionKey;
              }
              return false;
            });
  }

  private Optional<ProcessEntity> findProcess(
      final CamundaMigrator migrator, final long processDefinitionKey) {
    final var processDefinitionQuery =
        SearchQueryBuilders.term(ProcessIndex.KEY, processDefinitionKey);
    final var req =
        SearchQueryRequest.of(
            s ->
                s.query(processDefinitionQuery)
                    .index(migrator.indexFor(ProcessIndex.class).getAlias()));

    return migrator.getSearchClient().search(req, ProcessEntity.class).hits().stream()
        .findFirst()
        .map(SearchQueryHit::source);
  }
}
