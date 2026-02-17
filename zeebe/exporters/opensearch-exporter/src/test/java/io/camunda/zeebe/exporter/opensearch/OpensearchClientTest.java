/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.dto.BulkIndexResponse;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.http.entity.BasicHttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.OpenSearchClusterClient;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;

@Execution(ExecutionMode.CONCURRENT)
final class OpensearchClientTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private static final int PARTITION_ID = 1;

  private final OpenSearchClient openSearchClient = mock(OpenSearchClient.class);
  private final RestClient restClient = mock(RestClient.class);
  private final ProtocolFactory factory = new ProtocolFactory();
  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  private final BulkIndexRequest bulkRequest = new BulkIndexRequest();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);
  private final TemplateReader templateReader = new TemplateReader(config.index);

  private final OpensearchClient client =
      new OpensearchClient(
          config,
          bulkRequest,
          openSearchClient,
          restClient,
          indexRouter,
          templateReader,
          new OpensearchMetrics(new SimpleMeterRegistry()));

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.opensearch.TestSupport#provideValueTypes")
  void shouldPutIndexTemplate(final ValueType valueType) throws IOException {
    // given
    final ArgumentCaptor<PutIndexTemplateRequest> requestCaptor =
        ArgumentCaptor.forClass(PutIndexTemplateRequest.class);

    final org.opensearch.client.opensearch.indices.PutIndexTemplateResponse response =
        mock(org.opensearch.client.opensearch.indices.PutIndexTemplateResponse.class);
    when(response.acknowledged()).thenReturn(true);

    final OpenSearchIndicesClient indicesClient = mock(OpenSearchIndicesClient.class);
    when(openSearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.putIndexTemplate(requestCaptor.capture())).thenReturn(response);

    // when
    client.putIndexTemplate(valueType);

    // then
    final PutIndexTemplateRequest expectedRequest =
        templateReader.getPutIndexTemplateRequest(
            indexRouter.indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase()),
            valueType,
            indexRouter.searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase()),
            indexRouter.aliasNameForValueType(valueType));

    assertThat(requestCaptor.getValue()).isEqualTo(expectedRequest);
  }

  @Test
  void shouldPutComponentTemplate() throws IOException {
    // given
    final ArgumentCaptor<PutComponentTemplateRequest> requestCaptor =
        ArgumentCaptor.forClass(PutComponentTemplateRequest.class);

    final org.opensearch.client.opensearch.cluster.PutComponentTemplateResponse response =
        mock(org.opensearch.client.opensearch.cluster.PutComponentTemplateResponse.class);
    when(response.acknowledged()).thenReturn(true);

    final OpenSearchClusterClient clusterClient = mock(OpenSearchClusterClient.class);
    when(openSearchClient.cluster()).thenReturn(clusterClient);
    when(clusterClient.putComponentTemplate(requestCaptor.capture())).thenReturn(response);

    // when
    client.putComponentTemplate();

    // then
    final PutComponentTemplateRequest expectedRequest =
        templateReader.getComponentTemplatePutRequest(
            String.format("%s-%s", config.index.prefix, VersionUtil.getVersionLowerCase()));

    assertThat(requestCaptor.getValue()).isEqualTo(expectedRequest);
  }

  private <T> ArgumentCaptor<Request> mockClientResponse(final T content) throws IOException {
    final var httpEntity = new BasicHttpEntity();
    final var serializedContent = MAPPER.writeValueAsBytes(content);
    final var requestCaptor = ArgumentCaptor.forClass(Request.class);
    final var response = mock(Response.class);

    httpEntity.setContent(new ByteArrayInputStream(serializedContent));
    httpEntity.setContentLength(serializedContent.length);
    httpEntity.setContentType("application/json");

    when(response.getEntity()).thenReturn(httpEntity);
    when(restClient.performRequest(requestCaptor.capture())).thenReturn(response);

    return requestCaptor;
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

      // when
      client.flush();

      // then
      verify(restClient, never()).performRequest(any(Request.class));
    }

    @Test
    void shouldFlushBulk() throws IOException {
      // given
      config.bulk.size = 1;
      final ArgumentCaptor<Request> requestCaptor =
          mockClientResponse(new BulkIndexResponse(false, List.of()));

      // when
      client.index(
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion())),
          new RecordSequence(PARTITION_ID, 1));
      client.flush();

      // then
      assertThat(bulkRequest.isEmpty()).isTrue();
      assertThat(requestCaptor.getAllValues())
          .as("sent a bulk request only once")
          .hasSize(1)
          .first()
          .extracting(Request::getEndpoint)
          .isEqualTo("/_bulk");
    }

    @Test
    void shouldClearBulkOnSuccess() throws IOException {
      // given
      config.bulk.size = 1;
      mockClientResponse(new BulkIndexResponse(false, List.of()));

      // when
      client.index(
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion())),
          new RecordSequence(PARTITION_ID, 1));
      client.flush();

      // then
      assertThat(bulkRequest.isEmpty()).isTrue();
    }

    @Test
    void shouldNotClearBulkOnFailure() throws IOException {
      // given
      config.bulk.size = 1;
      final var failure = new OpensearchExporterException("Injected failure");
      doThrow(failure).when(restClient).performRequest(any());

      // when
      client.index(
          factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersion())),
          new RecordSequence(PARTITION_ID, 1));
      assertThatCode(client::flush).isEqualTo(failure);

      // then
      assertThat(bulkRequest.size()).isEqualTo(1);
    }
  }
}
