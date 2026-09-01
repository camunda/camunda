/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.impl.util.VersionUtil;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessDefinitionState;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Reproduces #61277: a process definition the engine is draining can stay {@code ACTIVE} in
 * secondary storage.
 *
 * <p>{@code Process:CREATED} is distributed to every partition. {@link
 * io.camunda.exporter.handlers.ProcessCreatedHandler} still runs {@code updateEntity} everywhere so
 * the process cache stays warm, but only partition 1 may index the shared process document. {@link
 * io.camunda.exporter.handlers.ProcessDrainingHandler} is also partition-1-only. If another
 * partition indexed {@code CREATED} after partition 1 had already written {@code DRAINING}, the
 * full-document index would clobber state back to {@code ACTIVE}.
 */
final class ProcessDrainingExportConsistencyIT {

  private static final long PROCESS_DEFINITION_KEY = 2_251_799_813_685_862L;
  private static final int DEPLOYMENT_PARTITION =
      DefaultExporterResourceProvider.PROCESS_DEFINITION_PARTITION;
  private static final int OTHER_PARTITION = DEPLOYMENT_PARTITION + 1;

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @RegisterExtension
  private static final CamundaExporterITTemplateExtension TEMPLATE_EXTENSION =
      new CamundaExporterITTemplateExtension(SEARCH_DB);

  private final ProtocolFactory factory = new ProtocolFactory();
  private final byte[] bpmnXml = readBpmn();

  private String testPrefix;

  @BeforeEach
  void beforeEach() {
    testPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase(Locale.ROOT);
  }

  @AfterEach
  void afterEach() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      SEARCH_DB.esClient().indices().delete(req -> req.index(testPrefix + "*"));
    }
    SEARCH_DB.osClient().indices().delete(req -> req.index(testPrefix + "*"));
  }

  @TestTemplate
  void shouldKeepDrainingWhenAnotherPartitionExportsCreatedLater(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given — partition 1 has already exported CREATED then DRAINING
    prepareConfig(config);
    createSchemas(config);
    final var processIndex = processIndexName(config);

    exportOnPartition(config, DEPLOYMENT_PARTITION, ProcessIntent.CREATED);
    exportOnPartition(config, DEPLOYMENT_PARTITION, ProcessIntent.DRAINING);
    clientAdapter.refresh(testPrefix);

    assertThat(clientAdapter.get(documentId(), processIndex, ProcessEntity.class).getState())
        .as("precondition: partition 1 left the definition DRAINING before the other CREATED")
        .isEqualTo(ProcessDefinitionState.DRAINING);

    // when — a lagging partition exports its own copy of CREATED for the same definition
    exportOnPartition(config, OTHER_PARTITION, ProcessIntent.CREATED);
    clientAdapter.refresh(testPrefix);

    // then — the late full-document index must not clobber DRAINING back to ACTIVE
    assertThat(clientAdapter.get(documentId(), processIndex, ProcessEntity.class).getState())
        .isEqualTo(ProcessDefinitionState.DRAINING);
  }

  private void prepareConfig(final ExporterConfiguration config) {
    config.getConnect().setIndexPrefix(testPrefix);
    config.getIndex().setNumberOfReplicas(0);
    config.getBulk().setSize(1);
  }

  private void exportOnPartition(
      final ExporterConfiguration config, final int partitionId, final ProcessIntent intent)
      throws IOException {
    final var exporter = new CamundaExporter();
    exporter.configure(
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>(config.getConnect().getType(), config))
            .setPartitionId(partitionId));
    exporter.open(new ExporterTestController());
    try {
      exporter.export(processRecord(intent, partitionId));
    } finally {
      exporter.close();
    }
  }

  private Record<Process> processRecord(final ProcessIntent intent, final int partitionId) {
    final Process value =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withBpmnProcessId("testProcessId")
            .withResource(bpmnXml)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS,
        r ->
            r.withIntent(intent)
                .withKey(PROCESS_DEFINITION_KEY)
                .withPartitionId(partitionId)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withValue(value));
  }

  private String processIndexName(final ExporterConfiguration config) {
    return new ProcessIndex(
            config.getConnect().getIndexPrefix(),
            ConnectionTypes.isElasticSearch(config.getConnect().getType()))
        .getFullQualifiedName();
  }

  private static String documentId() {
    return String.valueOf(PROCESS_DEFINITION_KEY);
  }

  private static byte[] readBpmn() {
    try (final var in =
        ProcessDrainingExportConsistencyIT.class
            .getClassLoader()
            .getResourceAsStream("process/test-process.bpmn")) {
      return Objects.requireNonNull(in).readAllBytes();
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to read test-process.bpmn", e);
    }
  }
}
