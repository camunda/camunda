/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.exporter.utils.NdJsonSizeUtil;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test that validates the accuracy of our NDJSON payload size measurement in {@link
 * NdJsonSizeUtil#measureNdJsonPayloadSize} against Elasticsearch's default {@code
 * http.max_content_length} of 100MB.
 *
 * <p>Payloads under 100MB should be accepted; payloads over 100MB should be rejected. This confirms
 * that our measurement accurately predicts whether ES will accept or reject a given bulk request.
 */
@Testcontainers
class ElasticsearchPayloadSizeIT {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchPayloadSizeIT.class);
  private static final String TEST_INDEX = "payload-size-test";

  /** ~1MB string used as the payload for each entity. */
  private static final String ONE_MB_PAYLOAD = "x".repeat(1024 * 1024);

  /** 100MB — the default ES http.max_content_length */
  private static final long ES_MAX_CONTENT_LENGTH_BYTES = 100L * 1024 * 1024;

  @Container
  private static final ElasticsearchContainer ES_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static ElasticsearchClient esClient;

  @BeforeAll
  static void setUp() throws IOException {
    final var config = new ConnectConfiguration();
    config.setUrl("http://" + ES_CONTAINER.getHost() + ":" + ES_CONTAINER.getMappedPort(9200));
    esClient = new ElasticsearchConnector(config).createClient();

    esClient.indices().create(c -> c.index(TEST_INDEX));
    LOG.info(
        "Created index '{}' on ES at {}:{}",
        TEST_INDEX,
        ES_CONTAINER.getHost(),
        ES_CONTAINER.getMappedPort(9200));
  }

  @AfterAll
  static void tearDown() {
    if (esClient != null) {
      try {
        esClient.indices().delete(d -> d.index(TEST_INDEX));
      } catch (final IOException e) {
        LOG.warn("Failed to delete test index", e);
      }
      try {
        esClient._transport().close();
      } catch (final IOException ex) {
        LOG.warn("Failed to close Elasticsearch transport", ex);
      }
    }
  }

  /**
   * Validates that our NDJSON payload size measurement accurately predicts whether Elasticsearch
   * will accept or reject a bulk request based on its default 100MB {@code
   * http.max_content_length}.
   */
  @ParameterizedTest(name = "shouldHandlePayloadOf{0}MbCorrectly")
  @ValueSource(ints = {50, 60, 70, 80, 90, 110})
  void shouldAcceptOrRejectBasedOnPayloadSize(final int targetMB) throws Exception {
    // given
    final BulkRequest bulkRequest = buildBulkRequest(targetMB);
    final long payloadBytes =
        NdJsonSizeUtil.measureNdJsonPayloadSize(bulkRequest, esClient._jsonpMapper()).totalBytes();

    // when/then
    if (payloadBytes < ES_MAX_CONTENT_LENGTH_BYTES) {
      esClient.bulk(bulkRequest);
    } else {
      assertThatThrownBy(() -> esClient.bulk(bulkRequest))
          .hasMessageContaining("413 Request Entity Too Large");
    }
  }

  private BulkRequest buildBulkRequest(final int targetMB) {
    final var bulkRequestBuilder = new BulkRequest.Builder().refresh(Refresh.True);
    final var batchRequest =
        new ElasticsearchBatchRequest(
            esClient, bulkRequestBuilder, new ElasticsearchScriptBuilder());

    for (int i = 0; i < targetMB; i++) {
      final var entity = new LargeEntity("test" + "-" + targetMB + "-" + i, ONE_MB_PAYLOAD);
      batchRequest.addWithId(TEST_INDEX, entity.getId(), entity);
    }
    return bulkRequestBuilder.build();
  }

  static class LargeEntity implements ExporterEntity<LargeEntity> {
    private String id;
    private String payload;

    LargeEntity(final String id, final String payload) {
      this.id = id;
      this.payload = payload;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public LargeEntity setId(final String id) {
      this.id = id;
      return this;
    }

    public String getPayload() {
      return payload;
    }

    public LargeEntity setPayload(final String payload) {
      this.payload = payload;
      return this;
    }
  }
}
