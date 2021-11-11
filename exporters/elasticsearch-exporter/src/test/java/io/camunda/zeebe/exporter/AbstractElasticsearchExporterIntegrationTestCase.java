/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.GetDocumentResponse;
import io.camunda.zeebe.exporter.dto.GetSettingsForIndicesResponse;
import io.camunda.zeebe.exporter.dto.GetSettingsForIndicesResponse.IndexSettings;
import io.camunda.zeebe.exporter.util.ElasticsearchContainer;
import io.camunda.zeebe.exporter.util.ElasticsearchNode;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.exporter.ExporterIntegrationRule;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.util.Map;
import org.awaitility.Awaitility;
import org.elasticsearch.client.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractElasticsearchExporterIntegrationTestCase {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  protected final ExporterIntegrationRule exporterBrokerRule = new ExporterIntegrationRule();

  protected ElasticsearchNode<ElasticsearchContainer> elastic;
  protected ElasticsearchExporterConfiguration configuration;
  protected ElasticsearchExporterFaultToleranceIT.ElasticsearchTestClient esClient;

  @Before
  public void setUp() {
    elastic = new ElasticsearchContainer().withEnv("ES_JAVA_OPTS", "-Xms750m -Xmx750m");
  }

  @After
  public void tearDown() throws IOException {
    if (esClient != null) {
      esClient.close();
      esClient = null;
    }

    exporterBrokerRule.stop();
    elastic.stop();
    configuration = null;
  }

  protected void assertIndexSettings() {
    final var settingsForIndices = esClient.getSettingsForIndices();
    final var indices = settingsForIndices.getIndices();
    assertThat(indices).isNotEmpty();

    for (final String indexName : indices.keySet()) {
      final IndexSettings settings = indices.get(indexName);
      final Integer numberOfShards = settings.getNumberOfShards();
      final Integer numberOfReplicas = settings.getNumberOfReplicas();

      final int expectedNumberOfShards = numberOfShardsForIndex(indexName);
      final int expectedNumberOfReplicas = numberOfReplicasForIndex();

      assertThat(numberOfShards)
          .withFailMessage(
              "Expected number of shards of index %s to be %d but was %d",
              indexName, expectedNumberOfShards, numberOfShards)
          .isEqualTo(expectedNumberOfShards);
      assertThat(numberOfReplicas)
          .withFailMessage(
              "Expected number of replicas of index %s to be %d but was %d",
              indexName, expectedNumberOfReplicas, numberOfReplicas)
          .isEqualTo(expectedNumberOfReplicas);
    }
  }

  protected void assertRecordExported(final Record<?> record) {
    Awaitility.await("Expected the record to be exported: " + record.toJson())
        .ignoreExceptionsInstanceOf(ElasticsearchExporterException.class)
        .untilAsserted(
            () -> {
              final Map<String, Object> source = esClient.getDocument(record);

              assertThat(source)
                  .withFailMessage("Failed to fetch record %s from elasticsearch", record)
                  .isNotNull()
                  .isEqualTo(recordToMap(record));
            });
  }

  protected ElasticsearchTestClient createElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration) {
    return new ElasticsearchTestClient(
        configuration, LoggerFactory.getLogger("io.camunda.zeebe.exporter.elasticsearch"));
  }

  protected Map<String, Object> recordToMap(final Record<?> record) {
    final JsonNode jsonNode;
    try {
      jsonNode = MAPPER.readTree(record.toJson());
    } catch (final IOException e) {
      throw new AssertionError("Failed to deserialize json of record " + record.toJson(), e);
    }

    return MAPPER.convertValue(jsonNode, new TypeReference<>() {});
  }

  private int numberOfReplicasForIndex() {
    if (configuration != null && configuration.index.getNumberOfReplicas() != null) {
      return configuration.index.getNumberOfReplicas();
    } else {
      return 0;
    }
  }

  private int numberOfShardsForIndex(final String indexName) {
    if (configuration != null && configuration.index.getNumberOfShards() != null) {
      return configuration.index.getNumberOfShards();
    } else if (indexName.startsWith(
            esClient.indexPrefixForValueTypeWithDelimiter(ValueType.PROCESS_INSTANCE))
        || indexName.startsWith(esClient.indexPrefixForValueTypeWithDelimiter(ValueType.JOB))) {
      return 3;
    } else {
      return 1;
    }
  }

  protected ElasticsearchExporterConfiguration getDefaultConfiguration() {
    final ElasticsearchExporterConfiguration configuration =
        new ElasticsearchExporterConfiguration();

    configuration.url = elastic.getRestHttpHost().toString();

    configuration.bulk.delay = 1;
    configuration.bulk.size = 1;

    configuration.index.prefix = "test-record";
    configuration.index.createTemplate = true;
    configuration.index.command = true;
    configuration.index.event = true;
    configuration.index.rejection = true;
    configuration.index.deployment = false;
    configuration.index.process = true;
    configuration.index.error = true;
    configuration.index.incident = true;
    configuration.index.job = true;
    configuration.index.jobBatch = true;
    configuration.index.message = true;
    configuration.index.messageSubscription = true;
    configuration.index.variable = true;
    configuration.index.variableDocument = true;
    configuration.index.processInstance = true;
    configuration.index.processInstanceCreation = true;
    configuration.index.processMessageSubscription = true;

    return configuration;
  }

  protected static class ElasticsearchTestClient extends ElasticsearchClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    ElasticsearchTestClient(
        final ElasticsearchExporterConfiguration configuration, final Logger log) {
      super(configuration, log);
    }

    GetSettingsForIndicesResponse getSettingsForIndices() {
      final var request = new Request("GET", "/_all/_settings");
      try {
        final var response = client.performRequest(request);
        final var statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() >= 400) {
          throw new ElasticsearchExporterException(
              "Failed to get index settings: " + statusLine.getReasonPhrase());
        }

        return MAPPER.readValue(
            response.getEntity().getContent(), GetSettingsForIndicesResponse.class);
      } catch (final IOException e) {
        throw new ElasticsearchExporterException("Failed to get index settings", e);
      }
    }

    Map<String, Object> getDocument(final Record<?> record) {
      final var request =
          new Request("GET", "/" + indexFor(record) + "/" + typeFor(record) + "/" + idFor(record));
      request.addParameter("routing", String.valueOf(record.getPartitionId()));
      try {
        final var response = client.performRequest(request);
        final var document =
            MAPPER.readValue(response.getEntity().getContent(), GetDocumentResponse.class);
        return document.getSource();
      } catch (final IOException e) {
        throw new ElasticsearchExporterException(
            "Failed to get record " + idFor(record) + " from index " + indexFor(record));
      }
    }
  }
}
