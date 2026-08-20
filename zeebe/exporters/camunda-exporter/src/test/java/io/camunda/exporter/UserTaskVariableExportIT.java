/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration tests verifying that the UserTaskVariableHandler skips exporting variables for
 * processes without user tasks. This is the core optimization from issue #47843.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class UserTaskVariableExportIT {

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private final ProtocolFactory factory = new ProtocolFactory();
  private CamundaExporter exporter;
  private ExporterTestController controller;

  @AfterEach
  void afterEach() throws IOException {
    if (exporter != null) {
      exporter.close();
    }
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  @TestTemplate
  void shouldExportVariableForProcessWithUserTasks(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
    exporter = createAndOpenExporter(config);

    final long processDefinitionKey = 1001L;
    final var bpmnResource =
        getClass().getClassLoader().getResource("process/two-process-with-embedded-form.bpmn");
    assertThat(bpmnResource).isNotNull();
    final byte[] bpmnBytes = Files.readAllBytes(Path.of(bpmnResource.getPath()));

    // Export a PROCESS:CREATED record for a process WITH user tasks
    final var processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withBrokerVersion("8.8.0")
                    .withValue(
                        ImmutableProcess.builder()
                            .from(factory.generateObject(ImmutableProcess.class))
                            .withProcessDefinitionKey(processDefinitionKey)
                            .withBpmnProcessId("testProcessIdOne")
                            .withResource(bpmnBytes)
                            .build()));
    exporter.export(processRecord);

    // when - export a VARIABLE:CREATED record for the same process
    final long scopeKey = 2001L;
    final long processInstanceKey = 2001L;
    final var variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r ->
                r.withIntent(VariableIntent.CREATED)
                    .withBrokerVersion("8.8.0")
                    .withValue(
                        ImmutableVariableRecordValue.builder()
                            .withName("myVar")
                            .withValue("\"hello\"")
                            .withProcessDefinitionKey(processDefinitionKey)
                            .withProcessInstanceKey(processInstanceKey)
                            .withScopeKey(scopeKey)
                            .withBpmnProcessId("testProcessIdOne")
                            .build()));
    exporter.export(variableRecord);
    controller.runScheduledTasks(Duration.ofSeconds(1));

    // then - the variable document should exist in the task template index
    clientAdapter.refresh();
    final var expectedDocId = scopeKey + "-myVar";
    final var taskIndexName = getTaskTemplateIndexName(config);
    final var document = clientAdapter.get(expectedDocId, taskIndexName, TaskVariableEntity.class);
    assertThat(document)
        .describedAs(
            "Variable should be exported to tasklist-task index for process with user tasks")
        .isNotNull();
  }

  @TestTemplate
  void shouldNotExportVariableForProcessWithoutUserTasks(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    createSchemas(config);
    exporter = createAndOpenExporter(config);

    final long processDefinitionKey = 3001L;
    final var bpmnResource = getClass().getClassLoader().getResource("process/test-process.bpmn");
    assertThat(bpmnResource).isNotNull();
    final byte[] bpmnBytes = Files.readAllBytes(Path.of(bpmnResource.getPath()));

    // Export a PROCESS:CREATED record for a process WITHOUT user tasks
    final var processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withBrokerVersion("8.8.0")
                    .withValue(
                        ImmutableProcess.builder()
                            .from(factory.generateObject(ImmutableProcess.class))
                            .withProcessDefinitionKey(processDefinitionKey)
                            .withBpmnProcessId("testProcessId")
                            .withResource(bpmnBytes)
                            .build()));
    exporter.export(processRecord);

    // when - export a VARIABLE:CREATED record for the same process
    final long scopeKey = 4001L;
    final long processInstanceKey = 4001L;
    final var variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r ->
                r.withIntent(VariableIntent.CREATED)
                    .withBrokerVersion("8.8.0")
                    .withValue(
                        ImmutableVariableRecordValue.builder()
                            .withName("myVar")
                            .withValue("\"world\"")
                            .withProcessDefinitionKey(processDefinitionKey)
                            .withProcessInstanceKey(processInstanceKey)
                            .withScopeKey(scopeKey)
                            .withBpmnProcessId("testProcessId")
                            .build()));
    exporter.export(variableRecord);
    controller.runScheduledTasks(Duration.ofSeconds(1));

    // then - the variable document should NOT exist in the task template index
    clientAdapter.refresh();
    final var expectedDocId = scopeKey + "-myVar";
    final var taskIndexName = getTaskTemplateIndexName(config);
    final var document = clientAdapter.get(expectedDocId, taskIndexName, TaskVariableEntity.class);
    assertThat(document)
        .describedAs(
            "Variable should NOT be exported to tasklist-task index for process without user tasks")
        .isNull();
  }

  private CamundaExporter createAndOpenExporter(final ExporterConfiguration config) {
    config.setSkipVariableWriteWithoutUserTasks(true);
    final var camundaExporter = new CamundaExporter();
    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("camundaExporter", config));
    camundaExporter.configure(context);
    controller = new ExporterTestController();
    camundaExporter.open(controller);
    return camundaExporter;
  }

  private static String getTaskTemplateIndexName(final ExporterConfiguration config) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    final var taskTemplate =
        new TaskTemplate(config.getConnect().getIndexPrefix(), isElasticsearch);
    return taskTemplate.getFullQualifiedName();
  }
}
