/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.tasks.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SchemaUpgradeClientReindexTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperFactory(
    new OptimizeDateTimeFormatterFactory().getObject(),
    ConfigurationServiceBuilder.createDefaultConfiguration()
  ).createOptimizeMapper();

  @Mock
  private ElasticSearchSchemaManager schemaManager;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS, strictness = Mock.Strictness.LENIENT)
  private OptimizeElasticsearchClient elasticsearchClient;
  @Mock
  private ConfigurationService configurationService;
  @Mock
  private OptimizeIndexNameService indexNameService;
  @Mock
  private ElasticsearchMetadataService metadataService;

  private SchemaUpgradeClient underTest;

  @RegisterExtension
  LogCapturer logCapturer = LogCapturer.create().captureForType(SchemaUpgradeClient.class);

  @BeforeEach
  public void init() {
    when(elasticsearchClient.getIndexNameService()).thenReturn(indexNameService);
    this.underTest = createSchemaUpgradeClient(
      schemaManager, metadataService, configurationService, elasticsearchClient
    );
  }

  @Test
  public void testSuccessfulReindexWithProgressCheck() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index1))).thenReturn(1L);
    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index2))).thenReturn(0L);
    when(elasticsearchClient.submitReindexTask(any(ReindexRequest.class)).getTask())
      .thenReturn(taskId);

    // the first task response is in progress, the second is successfully complete
    mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
      // then no exceptions are thrown
      .doesNotThrowAnyException();

    // and reindex was executed
    verify(elasticsearchClient).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testReindexDetectsPendingReindexAndWaitForIt() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String nodeId = "abc";
    final int numericTaskId = 12345;
    final String taskId = nodeId + ":" + numericTaskId;

    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index1))).thenReturn(1L);
    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index2))).thenReturn(0L);
    final TaskInfo taskInfo = mock(TaskInfo.class);
    when(elasticsearchClient.getTaskList(any(ListTasksRequest.class)).getTasks())
      .thenReturn(ImmutableList.of(taskInfo));
    when(taskInfo.getTaskId()).thenReturn(new TaskId(nodeId, numericTaskId));
    when(taskInfo.getDescription()).thenReturn(index1 + index2);

    mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
      // then no exceptions are thrown
      .doesNotThrowAnyException();

    // and the log contains expected entries
    logCapturer.assertContains(
      "Found pending reindex task with id [" + taskId + "] from index [" + index1 + "] to [" + index2
        + "], will wait for it to finish."
    );

    // and reindex was never submitted
    verify(elasticsearchClient, never()).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testReindexSkippedDueToEqualDocCount() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";

    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index1))).thenReturn(1L);
    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index2))).thenReturn(1L);

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
      // then no exceptions are thrown
      .doesNotThrowAnyException();

    logCapturer.assertContains(
      "Found that index [" + index2 + "] already contains the same amount of documents as ["
        + index1 + "], will skip reindex."
    );

    // and reindex was never submitted
    verify(elasticsearchClient, never()).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testFailOnReindexTaskSubmissionError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";

    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index1))).thenReturn(1L);
    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index2))).thenReturn(0L);
    given(elasticsearchClient.submitReindexTask(any(ReindexRequest.class))
            .getTask()).willAnswer(invocation -> {
      throw new IOException();
    });

    // when
    assertThatThrownBy(() -> underTest.reindex(index1, index2))
      // then an exception is thrown
      .isInstanceOf(UpgradeRuntimeException.class);

    // and reindex was submitted
    verify(elasticsearchClient).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testFailOnReindexTaskStatusCheckError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index1))).thenReturn(1L);
    when(elasticsearchClient.countWithoutPrefix(new CountRequest(index2))).thenReturn(0L);
    when(elasticsearchClient.submitReindexTask(any(ReindexRequest.class)).getTask()).thenReturn(taskId);

    // the task status response contains an error when checking for status
    final TaskResponse taskResponseWithError = new TaskResponse(
      true,
      new TaskResponse.Task(taskId, new TaskResponse.Status(1L, 0L, 0L, 0L)),
      new TaskResponse.Error(
        "error",
        "failed hard",
        Arrays.asList(
          "ctx._source.flowNodeData = ((Map) params.get(ctx._source.id)).values();\n",
          "                                                             ^---- HERE"
        ),
        ImmutableMap.of("type", "null_pointer_exception",
                        "reason", "someReason"
        )
      ),
      null
    );
    final Response taskStatusResponse = createEsResponse(taskResponseWithError);
    whenReindexStatusRequest(taskId).thenReturn(taskStatusResponse);

    // when
    assertThatThrownBy(() -> underTest.reindex(index1, index2))
      // then an exception is thrown
      .isInstanceOf(UpgradeRuntimeException.class)
      .hasMessage(taskResponseWithError.getError().toString());

    // and reindex was submitted
    verify(elasticsearchClient).submitReindexTask(any(ReindexRequest.class));
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
    return when(elasticsearchClient.performRequest(
      argThat(argument ->
                argument != null
                  && argument.getMethod().equals(HttpGet.METHOD_NAME)
                  && argument.getEndpoint().equals("/_tasks/" + taskId)
      )
    ));
  }

  private Response createEsResponse(TaskResponse taskResponse) throws IOException {
    final Response mockedReindexResponse = mock(Response.class);

    final HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(taskResponse)));
    when(mockedReindexResponse.getEntity()).thenReturn(httpEntity);

    return mockedReindexResponse;
  }

}
