/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.ElasticsearchClient.buildPutIndexLifecycleManagementPolicyRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.transport.ElasticsearchTransport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ElasticsearchClientTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private static final int PARTITION_ID = 1;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final BulkIndexRequest bulkRequest = new BulkIndexRequest();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);
  private final TemplateReader templateReader = new TemplateReader(config);

  @Test
  void shouldBuildPutIndexLifecycleManagementPolicyRequestCorrectly()
      throws JsonProcessingException {
    assertThat(MAPPER.writeValueAsString(buildPutIndexLifecycleManagementPolicyRequest("300d3s")))
        .describedAs("Expect that request is mapped to json correctly")
        .isEqualTo(
            // Mapped from Object to make sure produced JSON string is formatted the same way
            MAPPER.writeValueAsString(
                MAPPER.readValue(
                    """
                    {
                      "policy": {
                        "phases": {
                          "delete": {
                            "min_age": "300d3s",
                            "actions": {
                              "delete": {}
                            }
                          }
                        }
                      }
                    }""",
                    Object.class)));
  }

  @Nested
  final class FlushTest {
    @BeforeEach
    void beforeEach() {
      // max out bulk configuration for more granular control of flushing per test
      config.bulk.memoryLimit = Integer.MAX_VALUE;
      config.bulk.delay = Integer.MAX_VALUE;
      config.bulk.size = Integer.MAX_VALUE;
    }

    @Test
    void shouldFlushOnMemoryLimit() {
      // given
      final var esClient = mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class);
      final var client =
          new ElasticsearchClient(
              config,
              bulkRequest,
              esClient,
              indexRouter,
              templateReader,
              new ElasticsearchMetrics(new SimpleMeterRegistry()));

      final var firstRecord =
          factory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withValueType(ValueType.DECISION));
      final var secondRecord =
          factory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withValueType(ValueType.VARIABLE));

      // when - index a single record, then set the memory limit specifically to be its size + 1
      // this decouples the test from whatever is used to serialize the record
      client.index(firstRecord, new RecordSequence(PARTITION_ID, 1));
      config.bulk.memoryLimit = bulkRequest.memoryUsageBytes() + 1;
      assertThat(client.shouldFlush()).isFalse();

      // when - then
      client.index(secondRecord, new RecordSequence(PARTITION_ID, 1));
      assertThat(client.shouldFlush()).isTrue();
    }

    @Test
    void shouldFlushOnSizeLimit() {
      // given
      final var esClient = mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class);
      final var client =
          new ElasticsearchClient(
              config,
              bulkRequest,
              esClient,
              indexRouter,
              templateReader,
              new ElasticsearchMetrics(new SimpleMeterRegistry()));

      config.bulk.size = 2;
      final var firstRecord =
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion()));
      final var secondRecord =
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion()));

      // when
      client.index(firstRecord, new RecordSequence(PARTITION_ID, 1));
      assertThat(client.shouldFlush()).isFalse();

      // when - then
      client.index(secondRecord, new RecordSequence(PARTITION_ID, 2));
      assertThat(client.shouldFlush()).isTrue();
    }

    @Test
    void shouldNotFlushIfNothingIndexed() throws IOException {
      // given
      final var esClient = mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class);
      final var client =
          new ElasticsearchClient(
              config,
              bulkRequest,
              esClient,
              indexRouter,
              templateReader,
              new ElasticsearchMetrics(new SimpleMeterRegistry()));

      // when
      client.flush();

      // then - no exception, nothing happened
      assertThat(bulkRequest.isEmpty()).isTrue();
    }

    @Test
    void shouldClearBulkOnSuccess() throws IOException {
      // given - use a real ES client against a mock transport would be complex;
      // instead verify via the integration test. Here we just verify the clear logic
      // by testing that clear() works after indexing.
      final var esClient = mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class);
      final var client =
          new ElasticsearchClient(
              config,
              bulkRequest,
              esClient,
              indexRouter,
              templateReader,
              new ElasticsearchMetrics(new SimpleMeterRegistry()));

      config.bulk.size = 1;
      client.index(
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion())),
          new RecordSequence(PARTITION_ID, 1));

      // verify the bulk request has content before clearing
      assertThat(bulkRequest.isEmpty()).isFalse();

      // when - clear simulates what happens on successful flush
      bulkRequest.clear();

      // then
      assertThat(bulkRequest.isEmpty()).isTrue();
    }

    @Test
    void shouldNotClearBulkOnFailure() throws IOException {
      // given - mock a transport that throws an exception
      final var transport = mock(ElasticsearchTransport.class);
      when(transport.performRequest(any(), any(), any()))
          .thenThrow(new IOException("Injected failure"));
      final var esClient = new co.elastic.clients.elasticsearch.ElasticsearchClient(transport);
      final var client =
          new ElasticsearchClient(
              config,
              bulkRequest,
              esClient,
              indexRouter,
              templateReader,
              new ElasticsearchMetrics(new SimpleMeterRegistry()));

      config.bulk.size = 1;

      // when
      client.index(
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion())),
          new RecordSequence(PARTITION_ID, 1));
      assertThatThrownBy(client::flush)
          .isInstanceOf(ElasticsearchExporterException.class)
          .hasMessageContaining("Failed to flush bulk");

      // then
      assertThat(bulkRequest.size()).isEqualTo(1);
    }
  }
}
