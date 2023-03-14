/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.BulkIndexResponse;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.http.entity.BasicHttpEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

@Execution(ExecutionMode.CONCURRENT)
final class ElasticsearchClientTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private static final int PARTITION_ID = 1;

  private final RestClient restClient = mock(RestClient.class);
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final BulkIndexRequest bulkRequest = new BulkIndexRequest();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);
  private final TemplateReader templateReader = new TemplateReader(config.index);

  private final ElasticsearchClient client =
      new ElasticsearchClient(
          config,
          bulkRequest,
          restClient,
          indexRouter,
          templateReader,
          new ElasticsearchMetrics(PARTITION_ID));

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
  void shouldPutIndexTemplate(final ValueType valueType) throws IOException {
    // given
    final Template expectedTemplate =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType),
            indexRouter.aliasNameForValueType(valueType));
    final ArgumentCaptor<Request> requestCaptor =
        mockClientResponse(new PutIndexTemplateResponse(true));

    // when
    client.putIndexTemplate(valueType);

    // then
    final var request = requestCaptor.getValue();
    final var template = MAPPER.readValue(request.getEntity().getContent(), Template.class);
    assertThat(template).isEqualTo(expectedTemplate);
  }

  @Test
  void shouldPutComponentTemplate() throws IOException {
    // given
    final var expectedTemplate = templateReader.readComponentTemplate();
    final ArgumentCaptor<Request> requestCaptor = mockClientResponse(Map.of("acknowledged", true));

    // when
    client.putComponentTemplate();

    // then
    final var request = requestCaptor.getValue();
    final var template = MAPPER.readValue(request.getEntity().getContent(), Template.class);
    assertThat(template).isEqualTo(expectedTemplate);
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
      final var firstRecord = factory.generateRecord(ValueType.DECISION);
      final var secondRecord = factory.generateRecord(ValueType.VARIABLE);

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
      final var firstRecord = factory.generateRecord();
      final var secondRecord = factory.generateRecord();

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
      client.index(factory.generateRecord(), new RecordSequence(PARTITION_ID, 1));
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
      client.index(factory.generateRecord(), new RecordSequence(PARTITION_ID, 1));
      client.flush();

      // then
      assertThat(bulkRequest.isEmpty()).isTrue();
    }

    @Test
    void shouldNotClearBulkOnFailure() throws IOException {
      // given
      config.bulk.size = 1;
      final var failure = new ElasticsearchExporterException("Injected failure");
      doThrow(failure).when(restClient).performRequest(any());

      // when
      client.index(factory.generateRecord(), new RecordSequence(PARTITION_ID, 1));
      assertThatCode(client::flush).isEqualTo(failure);

      // then
      assertThat(bulkRequest.size()).isEqualTo(1);
    }
  }
}
