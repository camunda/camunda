/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.util.ElasticsearchContainer;
import io.zeebe.exporter.util.ElasticsearchNode;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.test.exporter.ExporterIntegrationRule;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.util.Map;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

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
    elastic = new ElasticsearchContainer();
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
    final ImmutableOpenMap<String, Settings> settingsForIndices = esClient.getSettingsForIndices();
    for (final ObjectCursor<String> key : settingsForIndices.keys()) {
      final String indexName = key.value;
      final Settings settings = settingsForIndices.get(indexName);
      final Integer numberOfShards = settings.getAsInt("index.number_of_shards", -1);
      final Integer numberOfReplicas = settings.getAsInt("index.number_of_replicas", -1);

      final int expectedNumberOfShards = numberOfShardsForIndex(indexName);

      assertThat(numberOfShards)
          .withFailMessage(
              "Expected number of shards of index %s to be %d but was %d",
              indexName, expectedNumberOfShards, numberOfShards)
          .isEqualTo(expectedNumberOfShards);
      assertThat(numberOfReplicas)
          .withFailMessage(
              "Expected number of replicas of index %s to be 0 but was %d",
              indexName, numberOfReplicas)
          .isEqualTo(0);
    }
  }

  protected void assertRecordExported(final Record<?> record) {
    final Map<String, Object> source = esClient.get(record);
    assertThat(source)
        .withFailMessage("Failed to fetch record %s from elasticsearch", record)
        .isNotNull();

    assertThat(source).isEqualTo(recordToMap(record));
  }

  protected ElasticsearchTestClient createElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration) {
    return new ElasticsearchTestClient(
        configuration, new ZbLogger("io.zeebe.exporter.elasticsearch"));
  }

  protected Map<String, Object> recordToMap(final Record<?> record) {
    final JsonNode jsonNode;
    try {
      jsonNode = MAPPER.readTree(record.toJson());
    } catch (final IOException e) {
      throw new AssertionError("Failed to deserialize json of record " + record.toJson(), e);
    }

    return MAPPER.convertValue(jsonNode, Map.class);
  }

  private int numberOfShardsForIndex(final String indexName) {
    if (indexName.startsWith(
            esClient.indexPrefixForValueTypeWithDelimiter(ValueType.WORKFLOW_INSTANCE))
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
    configuration.index.deployment = true;
    configuration.index.error = true;
    configuration.index.incident = true;
    configuration.index.job = true;
    configuration.index.jobBatch = true;
    configuration.index.message = true;
    configuration.index.messageSubscription = true;
    configuration.index.variable = true;
    configuration.index.variableDocument = true;
    configuration.index.workflowInstance = true;
    configuration.index.workflowInstanceCreation = true;
    configuration.index.workflowInstanceSubscription = true;

    return configuration;
  }

  protected static class ElasticsearchTestClient extends ElasticsearchClient {

    ElasticsearchTestClient(
        final ElasticsearchExporterConfiguration configuration, final Logger log) {
      super(configuration, log);
    }

    ImmutableOpenMap<String, Settings> getSettingsForIndices() {
      final GetSettingsRequest settingsRequest = new GetSettingsRequest();
      try {
        return client
            .indices()
            .getSettings(settingsRequest, RequestOptions.DEFAULT)
            .getIndexToSettings();
      } catch (final IOException e) {
        throw new ElasticsearchExporterException("Failed to get index settings", e);
      }
    }

    Map<String, Object> get(final Record<?> record) {
      final GetRequest request =
          new GetRequest(indexFor(record), typeFor(record), idFor(record))
              .routing(String.valueOf(record.getPartitionId()));
      try {
        final GetResponse response = client.get(request, RequestOptions.DEFAULT);
        if (response.isExists()) {
          return response.getSourceAsMap();
        } else {
          return null;
        }
      } catch (final IOException e) {
        throw new ElasticsearchExporterException(
            "Failed to get record " + idFor(record) + " from index " + indexFor(record));
      }
    }
  }
}
