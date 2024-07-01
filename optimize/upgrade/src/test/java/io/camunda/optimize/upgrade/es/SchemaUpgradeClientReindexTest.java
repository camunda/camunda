/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.upgrade.es;
// 
// import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
// import static io.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
// import static org.assertj.core.api.Assertions.assertThatCode;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.argThat;
// import static org.mockito.BDDMockito.given;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.never;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
// 
// import com.google.common.collect.ImmutableList;
// import com.google.common.collect.ImmutableMap;
// import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
// import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
// import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
// import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
// import io.camunda.optimize.service.util.configuration.ConfigurationService;
// import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
// import io.github.netmikey.logunit.api.LogCapturer;
// import java.io.ByteArrayInputStream;
// import java.io.IOException;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import lombok.SneakyThrows;
// import org.apache.http.HttpEntity;
// import org.apache.http.client.methods.HttpGet;
// import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
// import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
// import org.elasticsearch.client.Response;
// import org.elasticsearch.client.core.CountRequest;
// import org.elasticsearch.index.reindex.ReindexRequest;
// import org.elasticsearch.tasks.TaskId;
// import org.elasticsearch.tasks.TaskInfo;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.junit.jupiter.api.extension.RegisterExtension;
// import org.mockito.Answers;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.mockito.stubbing.OngoingStubbing;
// 
// @ExtendWith(MockitoExtension.class)
// public class SchemaUpgradeClientReindexTest {
//   @Mock private ElasticSearchSchemaManager schemaManager;
// 
//   @Mock(answer = Answers.RETURNS_DEEP_STUBS, strictness = Mock.Strictness.LENIENT)
//   private OptimizeElasticsearchClient elasticsearchClient;
// 
//   @Mock private ConfigurationService configurationService;
//   @Mock private OptimizeIndexNameService indexNameService;
//   @Mock private ElasticSearchMetadataService metadataService;
//   @Mock private TaskInfo taskInfo;
// 
//   private SchemaUpgradeClient underTest;
// 
//   @RegisterExtension
//   LogCapturer logCapturer = LogCapturer.create().captureForType(SchemaUpgradeClient.class);
// 
//   @BeforeEach
//   public void init() {
//     when(elasticsearchClient.getIndexNameService()).thenReturn(indexNameService);
//     this.underTest =
//         createSchemaUpgradeClient(
//             schemaManager, metadataService, configurationService, elasticsearchClient);
//   }
// 
//   @Test
//   public void testSuccessfulReindexWithProgressCheck() throws IOException {
//     // given
//     final String index1 = "index1";
//     final String index2 = "index2";
//     final String taskId = "12345:67890";
// 
//     mockCountResponseFromIndex(index1, 1L);
//     mockCountResponseFromIndex(index2, 0L);
//     when(elasticsearchClient.getTaskList(any()))
//         .thenReturn(
//             new ListTasksResponse(
//                 Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
//     when(elasticsearchClient.submitReindexTask(any(ReindexRequest.class)).getTask())
//         .thenReturn(taskId);
// 
//     // the first task response is in progress, the second is successfully complete
//     mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));
// 
//     // when
//     assertThatCode(() -> underTest.reindex(index1, index2))
//         // then no exceptions are thrown
//         .doesNotThrowAnyException();
// 
//     // and reindex was executed
//     verify(elasticsearchClient).submitReindexTask(any(ReindexRequest.class));
//   }
// 
//   @Test
//   public void testReindexDetectsPendingReindexAndWaitForIt() throws IOException {
//     // given
//     final String index1 = "index1";
//     final String index2 = "index2";
//     final String nodeId = "abc";
//     final int numericTaskId = 12345;
//     final String taskId = nodeId + ":" + numericTaskId;
// 
//     mockCountResponseFromIndex(index1, 1L);
//     mockCountResponseFromIndex(index2, 0L);
//     final TaskInfo taskInfo = mock(TaskInfo.class);
//     when(elasticsearchClient.getTaskList(any(ListTasksRequest.class)).getTasks())
//         .thenReturn(ImmutableList.of(taskInfo));
//     when(taskInfo.getTaskId()).thenReturn(new TaskId(nodeId, numericTaskId));
//     when(taskInfo.getDescription()).thenReturn(createReindexTaskDescription(index1, index2));
// 
//     mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));
// 
//     // when
//     assertThatCode(() -> underTest.reindex(index1, index2))
//         // then no exceptions are thrown
//         .doesNotThrowAnyException();
// 
//     // and the log contains expected entries
//     logCapturer.assertContains(
//         "Found pending task with id " + taskId + ", will wait for it to finish.");
// 
//     // and reindex was never submitted
//     verify(elasticsearchClient, never()).submitReindexTask(any(ReindexRequest.class));
//   }
// 
//   @Test
//   public void testReindexSkippedDueToEqualDocCount() throws IOException {
//     // given
//     final String index1 = "index1";
//     final String index2 = "index2";
// 
//     mockCountResponseFromIndex(index1, 1L);
//     mockCountResponseFromIndex(index2, 1L);
// 
//     // when
//     assertThatCode(() -> underTest.reindex(index1, index2))
//         // then no exceptions are thrown
//         .doesNotThrowAnyException();
// 
//     logCapturer.assertContains(
//         "Found that index ["
//             + index2
//             + "] already contains the same amount of documents as ["
//             + index1
//             + "], will skip reindex.");
// 
//     // and reindex was never submitted
//     verify(elasticsearchClient, never()).submitReindexTask(any(ReindexRequest.class));
//   }
// 
//   @Test
//   public void testFailOnReindexTaskSubmissionError() throws IOException {
//     // given
//     final String index1 = "index1";
//     final String index2 = "index2";
// 
//     mockCountResponseFromIndex(index1, 1L);
//     mockCountResponseFromIndex(index2, 0L);
//     when(elasticsearchClient.getTaskList(any()))
//         .thenReturn(
//             new ListTasksResponse(
//                 Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
//     given(elasticsearchClient.submitReindexTask(any(ReindexRequest.class)).getTask())
//         .willAnswer(
//             invocation -> {
//               throw new IOException();
//             });
// 
//     // when
//     assertThatThrownBy(() -> underTest.reindex(index1, index2))
//         // then an exception is thrown
//         .isInstanceOf(UpgradeRuntimeException.class);
// 
//     // and reindex was submitted
//     verify(elasticsearchClient).submitReindexTask(any(ReindexRequest.class));
//   }
// 
//   @Test
//   public void testFailOnReindexTaskStatusCheckError() throws IOException {
//     // given
//     final String index1 = "index1";
//     final String index2 = "index2";
//     final String taskId = "12345:67890";
// 
//     mockCountResponseFromIndex(index1, 1L);
//     mockCountResponseFromIndex(index2, 0L);
//     mockListTaskInfoResponseContainingSourceAndTarget(taskId, index1, index2);
// 
//     // the task status response contains an error when checking for status
//     final TaskResponse taskResponseWithError =
//         new TaskResponse(
//             true,
//             new TaskResponse.Task(taskId, new TaskResponse.Status(1L, 0L, 0L, 0L)),
//             new TaskResponse.Error(
//                 "error",
//                 "failed hard",
//                 Arrays.asList(
//                     "ctx._source.flowNodeData = ((Map) params.get(ctx._source.id)).values();\n",
//                     "                                                             ^---- HERE"),
//                 ImmutableMap.of(
//                     "type", "null_pointer_exception",
//                     "reason", "someReason")),
//             null);
//     final Response taskStatusResponse = createEsResponse(taskResponseWithError);
//     whenReindexStatusRequest(taskId).thenReturn(taskStatusResponse);
// 
//     // when
//     final String newReindexTaskId = "09876:54321";
//     when(elasticsearchClient.submitReindexTask(any(ReindexRequest.class)).getTask())
//         .thenReturn(newReindexTaskId);
//     mockReindexStatus(newReindexTaskId, new TaskResponse.Status(20L, 3L, 3L, 4L));
//     assertThatCode(() -> underTest.reindex(index1, index2))
//         // then no exceptions are thrown
//         .doesNotThrowAnyException();
// 
//     // then we detect the task that cannot be completed
//     logCapturer.assertContains(
//         "Pending task is not completable, submitting new task for identifier");
//     // and a new reindex task is submitted
//     verify(elasticsearchClient).submitReindexTask(any(ReindexRequest.class));
//   }
// 
//   @SneakyThrows
//   private void mockReindexStatus(final String taskId, final TaskResponse.Status inProgressStatus) {
//     final Response completedResponse =
//         createEsResponse(
//             new TaskResponse(
//                 true,
//                 new TaskResponse.Task(taskId, new TaskResponse.Status(20L, 6L, 6L, 8L)),
//                 null,
//                 null));
//     Response progressResponse = null;
//     if (inProgressStatus != null) {
//       progressResponse =
//           createEsResponse(
//               new TaskResponse(false, new TaskResponse.Task(taskId, inProgressStatus), null, null));
//     }
//     OngoingStubbing<Response> responseOngoingStubbing = whenReindexStatusRequest(taskId);
//     if (progressResponse != null) {
//       responseOngoingStubbing = responseOngoingStubbing.thenReturn(progressResponse);
//     }
//     responseOngoingStubbing.thenReturn(completedResponse);
//   }
// 
//   private void mockListTaskInfoResponseContainingSourceAndTarget(
//       final String taskId, final String sourceIndex, final String targetIndex) throws IOException {
//     when(taskInfo.getDescription())
//         .thenReturn(createReindexTaskDescription(sourceIndex, targetIndex));
//     when(taskInfo.getTaskId()).thenReturn(new TaskId(taskId));
//     when(elasticsearchClient.getTaskList(any()))
//         .thenReturn(
//             new ListTasksResponse(
//                 List.of(taskInfo), Collections.emptyList(), Collections.emptyList()));
//   }
// 
//   private String createReindexTaskDescription(
//       final String sourceIndexName, final String targetIndexName) {
//     return new ReindexRequest()
//         .setSourceIndices(sourceIndexName)
//         .setDestIndex(targetIndexName)
//         .setRefresh(true)
//         .getDescription();
//   }
// 
//   @SneakyThrows
//   private OngoingStubbing<Response> whenReindexStatusRequest(final String taskId) {
//     return when(
//         elasticsearchClient.performRequest(
//             argThat(
//                 argument ->
//                     argument != null
//                         && argument.getMethod().equals(HttpGet.METHOD_NAME)
//                         && argument.getEndpoint().equals("/_tasks/" + taskId))));
//   }
// 
//   @SneakyThrows
//   private void mockCountResponseFromIndex(final String indexName, final long count) {
//     when(elasticsearchClient.countWithoutPrefix(new CountRequest(indexName))).thenReturn(count);
//   }
// 
//   private Response createEsResponse(TaskResponse taskResponse) throws IOException {
//     final Response mockedReindexResponse = mock(Response.class);
// 
//     final HttpEntity httpEntity = mock(HttpEntity.class);
//     when(httpEntity.getContent())
//         .thenReturn(new ByteArrayInputStream(OPTIMIZE_MAPPER.writeValueAsBytes(taskResponse)));
//     when(mockedReindexResponse.getEntity()).thenReturn(httpEntity);
// 
//     return mockedReindexResponse;
//   }
// }
