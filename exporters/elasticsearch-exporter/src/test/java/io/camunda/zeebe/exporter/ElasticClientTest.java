/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.cluster.PutComponentTemplateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.BulkRequestAction;
import io.camunda.zeebe.exporter.dto.BulkRequestAction.IndexAction;
import io.camunda.zeebe.exporter.dto.BulkResponse;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
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
import org.mockito.Mock;

@Execution(ExecutionMode.CONCURRENT)
final class ElasticClientTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  @Mock private final RestClient restClient = mock(RestClient.class);
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final List<String> bulkRequest = new ArrayList<>();
  private final IndexRouter indexRouter = new IndexRouter(config.index);
  private final TemplateReader templateReader = new TemplateReader(config.index);

  // set the metrics directly to avoid having to index a record to create them
  private final ElasticClient client =
      new ElasticClient(config, bulkRequest, restClient, indexRouter, templateReader)
          .setMetrics(new ElasticsearchMetrics(1));

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
    final ArgumentCaptor<Request> requestCaptor =
        mockClientResponse(new PutComponentTemplateResponse.Builder().acknowledged(true).build());

    // when
    client.putComponentTemplate();

    // then
    final var request = requestCaptor.getValue();
    final var template = MAPPER.readValue(request.getEntity().getContent(), Template.class);
    assertThat(template).isEqualTo(expectedTemplate);
  }

  private static Stream<ValueType> provideValueTypes() {
    final var excludedValueTypes =
        EnumSet.of(
            ValueType.SBE_UNKNOWN,
            ValueType.NULL_VAL,
            ValueType.TIMER,
            ValueType.PROCESS_INSTANCE_RESULT,
            ValueType.DEPLOYMENT_DISTRIBUTION,
            ValueType.PROCESS_EVENT,
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
    return EnumSet.complementOf(excludedValueTypes).stream();
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
  final class IndexTest {
    @Test
    void shouldIgnoreRecordIfDuplicateOfLast() {
      // given
      final Record<RecordValue> record = factory.generateRecord();
      final Record<RecordValue> copiedRecord = ImmutableRecord.builder().from(record).build();
      client.index(record);

      // when
      client.index(copiedRecord);

      // then
      assertThat(bulkRequest).hasSize(1);
    }

    @Test
    void shouldIndexRecordAsBulkCommand() throws JsonProcessingException {
      // given
      final Record<RecordValue> record = factory.generateRecord();
      final var expectedBulkAction =
          new BulkRequestAction(
              new IndexAction(
                  indexRouter.indexFor(record),
                  indexRouter.idFor(record),
                  String.valueOf(record.getPartitionId())));

      // when
      client.index(record);

      // then - sort of clunky as we pre-serialize things
      final String[] bulkCommandParts = bulkRequest.get(0).split("\n");
      final var action = MAPPER.readValue(bulkCommandParts[0], BulkRequestAction.class);
      final var indexedRecord = MAPPER.readValue(bulkCommandParts[1], Record.class);
      assertThat(action).isEqualTo(expectedBulkAction);
      assertThat(indexedRecord).isEqualTo(record);
    }
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
      final var firstRecord = factory.generateRecord(b -> b.withRecordType(RecordType.COMMAND));
      final var secondRecord =
          ImmutableRecord.builder()
              .from(firstRecord)
              .withRecordType(RecordType.COMMAND_REJECTION)
              .build();

      // when - index a single record, then set the memory limit specifically to be its size + 1
      // this decouples the test from whatever is used to serialize the record
      client.index(firstRecord);
      config.bulk.memoryLimit = bulkRequest.get(0).length() + 1;
      assertThat(client.shouldFlush()).isFalse();

      // when - then
      client.index(secondRecord);
      assertThat(client.shouldFlush()).isTrue();
    }

    @Test
    void shouldFlushOnSizeLimit() {
      // given
      config.bulk.size = 2;
      final var firstRecord = factory.generateRecord();
      final var secondRecord = factory.generateRecord();

      // when
      client.index(firstRecord);
      assertThat(client.shouldFlush()).isFalse();

      // when - then
      client.index(secondRecord);
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
      final ArgumentCaptor<Request> requestCaptor = mockClientResponse(new BulkResponse());

      // when
      client.index(factory.generateRecord());
      client.flush();

      // then
      assertThat(bulkRequest).isEmpty();
      assertThat(requestCaptor.getAllValues())
          .as("sent a bulk request only once")
          .hasSize(1)
          .first()
          .extracting(Request::getEndpoint)
          .isEqualTo("/_bulk");
    }
  }
}
