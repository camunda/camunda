/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicStatusLine;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SchemaUpgradeClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperFactory(
    new OptimizeDateTimeFormatterFactory().getObject(),
    ConfigurationServiceBuilder.createDefaultConfiguration()
  ).createOptimizeMapper();

  @Mock
  private ElasticSearchSchemaManager schemaManager;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS, lenient = true)
  private OptimizeElasticsearchClient elasticsearchClient;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS, lenient = true)
  private RestHighLevelClient highLevelRestClient;
  @Mock
  private RestClient lowLevelRestClient;
  @Mock
  private ConfigurationService configurationService;
  @Mock
  private OptimizeIndexNameService indexNameService;
  @Mock
  private ElasticsearchMetadataService metadataService;

  private SchemaUpgradeClient underTest;

  @BeforeEach
  public void init() {
    when(elasticsearchClient.getHighLevelClient()).thenReturn(highLevelRestClient);
    when(elasticsearchClient.getIndexNameService()).thenReturn(indexNameService);
    when(highLevelRestClient.getLowLevelClient()).thenReturn(lowLevelRestClient);
    // just using the optimize data format here to satisfy the ObjectMapperFactory ctor
    when(configurationService.getEngineDateFormat()).thenReturn(ElasticsearchConstants.OPTIMIZE_DATE_FORMAT);
    this.underTest = new SchemaUpgradeClient(schemaManager, metadataService, elasticsearchClient, configurationService);
  }

  @Test
  public void testSuccessfulReindexWithProgressCheck() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    // the task is successfully submitted
    when(highLevelRestClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT)).getTask())
      .thenReturn(taskId);

    // the first task response is in progress, the second is successfully complete
    mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));

    // then no exceptions are thrown
    assertThatCode(() -> underTest.reindex(index1, index2)).doesNotThrowAnyException();
  }

  @Test
  public void testFailOnReindexTaskSubmissionError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";

    // the task cannot be submitted
    given(highLevelRestClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT))
            .getTask()).willAnswer(invocation -> {
      throw new IOException();
    });

    // then an exception is thrown
    assertThatThrownBy(() -> underTest.reindex(index1, index2)).isInstanceOf(UpgradeRuntimeException.class);
  }

  @Test
  public void testFailOnReindexTaskStatusCheckError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    // the task is successfully submitted
    when(highLevelRestClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT))
           .getTask()).thenReturn(taskId);

    // but the task status response contains an error when checking for status
    final TaskResponse taskResponseWithError = new TaskResponse(
      true,
      new TaskResponse.Task(taskId, new TaskResponse.Status(1L, 0L, 0L, 0L)),
      new TaskResponse.Error("error", "failed hard", "reindex"),
      null
    );
    final Response taskStatusResponse = createEsResponse(taskResponseWithError);
    whenReindexStatusRequest(taskId).thenReturn(taskStatusResponse);

    // then an exception is thrown
    assertThatThrownBy(() -> underTest.reindex(index1, index2))
      .isInstanceOf(UpgradeRuntimeException.class)
      .hasMessage(taskResponseWithError.getError().toString());
  }

  @SneakyThrows
  private void mockReindexStatus(final String taskId, final TaskResponse.Status inProgressStatus) {
    final Response completedResponse = createEsResponse(new TaskResponse(
      true, new TaskResponse.Task(taskId, new TaskResponse.Status(20L, 6L, 6L, 8L)), null, null
    ));
    Response progressResponse = null;
    if (inProgressStatus != null) {
      progressResponse = createEsResponse(new TaskResponse(
        false, new TaskResponse.Task(taskId, inProgressStatus), null, null
      ));
    }
    OngoingStubbing<Response> responseOngoingStubbing = whenReindexStatusRequest(taskId);
    if (progressResponse != null) {
      responseOngoingStubbing = responseOngoingStubbing.thenReturn(progressResponse);
    }
    responseOngoingStubbing.thenReturn(completedResponse);
  }

  @SneakyThrows
  private OngoingStubbing<Response> whenReindexStatusRequest(final String taskId) {
    return when(lowLevelRestClient.performRequest(
      argThat(argument ->
                argument != null
                  && argument.getMethod().equals(HttpGet.METHOD_NAME)
                  && argument.getEndpoint().equals("/_tasks/" + taskId)
      )
    ));
  }

  private Response createEsResponse(TaskResponse taskResponse) throws IOException {
    final ProtocolVersion protocolVersion = new ProtocolVersion("http", 1, 1);

    final Response mockedReindexResponse = mock(Response.class);
    when(mockedReindexResponse.getStatusLine())
      .thenReturn(new BasicStatusLine(protocolVersion, javax.ws.rs.core.Response.Status.OK.getStatusCode(), "OK"));

    final HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(taskResponse)));
    when(mockedReindexResponse.getEntity()).thenReturn(httpEntity);

    return mockedReindexResponse;
  }

}
