/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.exporter.utils.OpensearchNdJsonSizeUtil;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OpensearchPayloadSizeIT {

  private static final Logger LOG = LoggerFactory.getLogger(OpensearchPayloadSizeIT.class);
  private static final String TEST_INDEX = "payload-size-test";
  private static final String ONE_MB_PAYLOAD = "x".repeat(1024 * 1024);
  private static final long OS_MAX_CONTENT_LENGTH_BYTES = 100L * 1024 * 1024;

  @Container
  private static final OpenSearchContainer<?> OS_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static OpenSearchClient osClient;

  @BeforeAll
  static void setUp() throws IOException {
    final var config = new ConnectConfiguration();
    config.setUrl("http://" + OS_CONTAINER.getHost() + ":" + OS_CONTAINER.getMappedPort(9200));
    config.setType("opensearch");
    osClient = new OpensearchConnector(config).createClient();
    osClient.indices().create(c -> c.index(TEST_INDEX));
  }

  @AfterAll
  static void tearDown() {
    if (osClient != null) {
      try {
        osClient.indices().delete(d -> d.index(TEST_INDEX));
      } catch (final IOException e) {
        LOG.warn("Failed to delete test index", e);
      }
      try {
        osClient._transport().close();
      } catch (final IOException ex) {
        LOG.warn("Failed to close OpenSearch transport", ex);
      }
    }
  }

  @ParameterizedTest(name = "shouldAcceptPayloadOf{0}Mb")
  @ValueSource(ints = {50, 60, 70, 80, 90})
  void shouldAcceptPayloadUnderLimit(final int targetMB) throws Exception {
    final BulkRequest bulkRequest = buildBulkRequest(targetMB);
    final long payloadBytes =
        OpensearchNdJsonSizeUtil.measureNdJsonPayloadSize(
                bulkRequest, osClient._transport().jsonpMapper())
            .totalBytes();
    assertThat(payloadBytes).isLessThan(OS_MAX_CONTENT_LENGTH_BYTES);
    osClient.bulk(bulkRequest);
  }

  @Test
  void shouldRejectPayloadOverLimit() {
    final int targetMB = 110;
    final BulkRequest bulkRequest = buildBulkRequest(targetMB);
    final long payloadBytes =
        OpensearchNdJsonSizeUtil.measureNdJsonPayloadSize(
                bulkRequest, osClient._transport().jsonpMapper())
            .totalBytes();
    assertThat(payloadBytes).isGreaterThanOrEqualTo(OS_MAX_CONTENT_LENGTH_BYTES);
    assertThatThrownBy(() -> osClient.bulk(bulkRequest))
        .satisfiesAnyOf(
            t -> assertThat(t).hasMessageContaining("413 Request Entity Too Large"),
            t -> assertThat(t).hasMessageContaining("Broken pipe"),
            t -> assertThat(t).hasMessageContaining("Connection reset"));
  }

  private BulkRequest buildBulkRequest(final int targetMB) {
    final var bulkRequestBuilder = new BulkRequest.Builder().refresh(Refresh.True);
    for (int i = 0; i < targetMB; i++) {
      final var entity = new LargeEntity("test-" + targetMB + "-" + i, ONE_MB_PAYLOAD);
      bulkRequestBuilder.operations(
          op -> op.index(idx -> idx.index(TEST_INDEX).id(entity.getId()).document(entity)));
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
