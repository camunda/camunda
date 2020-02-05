/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicStatusLine;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EsIndexAdjusterIT {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperFactory(
    new OptimizeDateTimeFormatterFactory().getObject(),
    ConfigurationServiceBuilder.createDefaultConfiguration()
  ).createOptimizeMapper();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RestHighLevelClient restClient;
  @Mock
  private RestClient lowLevelRestClient;
  @Mock
  private ConfigurationService configurationService;
  @Mock
  private OptimizeIndexNameService indexNameService;

  @BeforeEach
  public void init() {
    when(restClient.getLowLevelClient()).thenReturn(lowLevelRestClient);
  }

  @Test
  public void testSuccessfulReindexWithProgressCheck() throws IOException {
    // given
    final ESIndexAdjuster underTest = new ESIndexAdjuster(restClient, indexNameService, configurationService);
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    // the task is successfully submitted
    when(restClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT)).getTask()).thenReturn(
      taskId);

    // the first task response is in progress, the second is successfully complete
    final Response inProgressResponse = createEsResponse(new TaskResponse(
      false,
      new TaskResponse.Task(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L)),
      null,
      null
    ));
    final Response completedResponse = createEsResponse(new TaskResponse(
      true,
      new TaskResponse.Task(taskId, new TaskResponse.Status(20L, 6L, 6L, 8L)),
      null,
      null
    ));
    when(lowLevelRestClient.performRequest(
      argThat(argument ->
                argument != null
                  && argument.getMethod().equals(HttpGet.METHOD_NAME)
                  && argument.getEndpoint().equals("/_tasks/" + taskId)
      )
    )).thenReturn(inProgressResponse).thenReturn(completedResponse);

    // then no exceptions are thrown
    underTest.reindex(index1, index2);
  }

  @Test
  public void testFailOnReindexTaskSubmissionError() throws IOException {
    // given
    final ESIndexAdjuster underTest = new ESIndexAdjuster(restClient, indexNameService, configurationService);
    final String index1 = "index1";
    final String index2 = "index2";

    // the task cannot be submitted
    given(restClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT))
            .getTask()).willAnswer(invocation -> { throw new IOException(); });

    // then an exception is thrown
    assertThrows(UpgradeRuntimeException.class, () -> underTest.reindex(index1, index2));
  }

  @Test
  public void testFailOnReindexTaskStatusCheckError() throws IOException {
    // given
    final ESIndexAdjuster underTest = new ESIndexAdjuster(restClient, indexNameService, configurationService);
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    // the task is successfully submitted
    when(restClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT)).getTask()).thenReturn(
      taskId);

    // the task response contains an error when checking for status
    final TaskResponse taskResponseWithError = new TaskResponse(
      true,
      new TaskResponse.Task(taskId, new TaskResponse.Status(1L, 0L, 0L, 0L)),
      new TaskResponse.Error("error", "failed hard", "reindex"),
      null
    );
    final Response taskStatusResponse = createEsResponse(taskResponseWithError);
    when(lowLevelRestClient.performRequest(
      argThat(argument ->
                argument != null
                  && argument.getMethod().equals(HttpGet.METHOD_NAME)
                  && argument.getEndpoint().equals("/_tasks/" + taskId)
      )
    )).thenReturn(taskStatusResponse);

    // then an exception is thrown
    UpgradeRuntimeException exception =
      assertThrows(UpgradeRuntimeException.class, () -> underTest.reindex(index1, index2));
    assertThat(exception.getMessage(), is(taskResponseWithError.getError().toString()));
  }

  private Response createEsResponse(TaskResponse taskResponse) throws IOException {
    final ProtocolVersion protocolVersion = new ProtocolVersion("http", 1, 1);

    final Response mockedReindexResponse = mock(Response.class);
    when(mockedReindexResponse.getStatusLine())
      .thenReturn(new BasicStatusLine(protocolVersion, 200, "OK"));

    final HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(taskResponse)));
    when(mockedReindexResponse.getEntity()).thenReturn(httpEntity);

    return mockedReindexResponse;
  }

}
