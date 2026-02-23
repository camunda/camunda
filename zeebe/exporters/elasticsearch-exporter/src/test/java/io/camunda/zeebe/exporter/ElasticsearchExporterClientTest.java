/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleResponse;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateResponse;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportOptions;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class ElasticsearchExporterClientTest {
  private static final int PARTITION_ID = 1;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final BulkIndexRequest bulkRequest = new BulkIndexRequest();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);
  private final TemplateReader templateReader = new TemplateReader(config);

  /**
   * Mocks the given {@link ElasticsearchTransport} so that any call to {@link
   * ElasticsearchTransport#performRequest} captures the request object into the returned {@link
   * AtomicReference} and responds with the given {@code response}.
   *
   * <p>This is the Java-client equivalent of the old REST-client helper that mocked {@code
   * RestClient.performRequest} and returned an {@code ArgumentCaptor<Request>}.
   *
   * @param transport the mocked transport to configure
   * @param response the response object to return (e.g. {@link PutIndexTemplateResponse}, {@link
   *     BulkResponse})
   * @return an {@link AtomicReference} that will hold the captured request after the call
   */
  @SuppressWarnings("unchecked")
  private <T> AtomicReference<T> mockTransportResponse(
      final ElasticsearchTransport transport, final Object response) throws IOException {
    final var requestCaptor = new AtomicReference<T>();
    when(transport.options()).thenReturn(mock(TransportOptions.class));
    when(transport.performRequest(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              requestCaptor.set((T) invocation.getArgument(0));
              return response;
            });
    return requestCaptor;
  }

  @Test
  void shouldBuildPutIndexLifecycleManagementPolicyRequestCorrectly() throws IOException {
    // given
    config.retention.setEnabled(true);
    config.retention.setMinimumAge("300d3s");
    config.retention.setPolicyName("test-ilm-policy");

    final var transport = mock(ElasticsearchTransport.class);
    final var putLifecycleResponse = PutLifecycleResponse.of(b -> b.acknowledged(true));
    final AtomicReference<PutLifecycleRequest> requestCaptor =
        mockTransportResponse(transport, putLifecycleResponse);
    final var esClient = new ElasticsearchClient(transport);
    final var client =
        new ElasticsearchExporterClient(
            config,
            bulkRequest,
            esClient,
            indexRouter,
            templateReader,
            new ElasticsearchMetrics(new SimpleMeterRegistry()));

    // when
    client.putIndexLifecycleManagementPolicy();

    // then
    final var capturedRequest = requestCaptor.get();
    assertThat(capturedRequest).isNotNull();
    assertThat(capturedRequest.name()).isEqualTo("test-ilm-policy");

    final var deletePhase = capturedRequest.policy().phases().delete();
    assertThat(deletePhase).isNotNull();
    assertThat(deletePhase.minAge()).isNotNull();
    assertThat(deletePhase.minAge().time()).isEqualTo("300d3s");
    assertThat(deletePhase.actions()).isNotNull();
    assertThat(deletePhase.actions().delete()).isNotNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
  void shouldPutIndexTemplate(final ValueType valueType) throws IOException {
    // given
    final var expectedTemplate =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase()),
            indexRouter.aliasNameForValueType(valueType));

    final var transport = mock(ElasticsearchTransport.class);
    final var putTemplateResponse = PutIndexTemplateResponse.of(b -> b.acknowledged(true));
    final AtomicReference<PutIndexTemplateRequest> requestCaptor =
        mockTransportResponse(transport, putTemplateResponse);
    final var esClient = new ElasticsearchClient(transport);
    final var client =
        new ElasticsearchExporterClient(
            config,
            bulkRequest,
            esClient,
            indexRouter,
            templateReader,
            new ElasticsearchMetrics(new SimpleMeterRegistry()));

    // when
    client.putIndexTemplate(valueType);

    // then — verify the request captured by the transport matches the expected template
    final var capturedRequest = requestCaptor.get();
    assertThat(capturedRequest).isNotNull();
    assertThat(capturedRequest.indexPatterns()).isEqualTo(expectedTemplate.patterns());
    assertThat(capturedRequest.composedOf()).isEqualTo(expectedTemplate.composedOf());
    assertThat(capturedRequest.name())
        .isEqualTo(
            indexRouter.indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase()));
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
      final var esClient = mock(ElasticsearchClient.class);
      final var client =
          new ElasticsearchExporterClient(
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
      final var esClient = mock(ElasticsearchClient.class);
      final var client =
          new ElasticsearchExporterClient(
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
      final var esClient = mock(ElasticsearchClient.class);
      final var client =
          new ElasticsearchExporterClient(
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
      // given
      final var esClient = mock(ElasticsearchClient.class);
      final var client =
          new ElasticsearchExporterClient(
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
      final var esClient = new ElasticsearchClient(transport);
      final var client =
          new ElasticsearchExporterClient(
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

    @Test
    void shouldSplitBulkIntoChunksWhenExceedingMemoryLimit() throws IOException {
      // given — mock a transport that returns successful (no-error) bulk responses
      final var transport = mock(ElasticsearchTransport.class);
      final var bulkResponse =
          BulkResponse.of(b -> b.errors(false).items(Collections.emptyList()).took(1));
      mockTransportResponse(transport, bulkResponse);
      final var esClient = new ElasticsearchClient(transport);

      final var localBulkRequest = new BulkIndexRequest();
      final var client =
          new ElasticsearchExporterClient(
              config,
              localBulkRequest,
              esClient,
              indexRouter,
              templateReader,
              new ElasticsearchMetrics(new SimpleMeterRegistry()));

      // Index 3 records — set the memory limit so that exactly 2 records fit per chunk,
      // which should produce 2 bulk calls
      final var firstRecord =
          factory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withValueType(ValueType.VARIABLE));
      final var secondRecord =
          factory.generateRecord(
              r -> r.withBrokerVersion(VersionUtil.getVersion()).withValueType(ValueType.DECISION));
      final var thirdRecord =
          factory.generateRecord(
              r ->
                  r.withBrokerVersion(VersionUtil.getVersion())
                      .withValueType(ValueType.PROCESS_INSTANCE));

      client.index(firstRecord, new RecordSequence(PARTITION_ID, 1));
      final int singleRecordSize = localBulkRequest.memoryUsageBytes();
      client.index(secondRecord, new RecordSequence(PARTITION_ID, 2));
      client.index(thirdRecord, new RecordSequence(PARTITION_ID, 3));

      // Set memory limit to just over 2x one record size, so 3 records require 2 chunks
      config.bulk.memoryLimit = singleRecordSize * 2 + 1;

      // when
      client.flush();

      // then — the transport should have been called exactly 2 times (2 chunks)
      verify(transport, times(2)).performRequest(any(), any(), any());
    }
  }
}
