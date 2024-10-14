/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.os;

import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static io.camunda.optimize.upgrade.db.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.service.db.os.ExtendedOpenSearchClient;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.github.netmikey.logunit.api.LogCapturer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.opensearch.client.Response;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.Retries;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;
import org.opensearch.client.opensearch.tasks.ListRequest;
import org.opensearch.client.opensearch.tasks.ListResponse;
import org.opensearch.client.opensearch.tasks.Status;

@ExtendWith(MockitoExtension.class)
public class SchemaUpgradeClientOSReindexTest {
  @Mock private OpenSearchSchemaManager schemaManager;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS, strictness = Mock.Strictness.LENIENT)
  private OptimizeOpenSearchClient openSearchClient;

  @Mock private ConfigurationService configurationService;
  @Mock private OptimizeIndexNameService indexNameService;
  @Mock private OpenSearchMetadataService metadataService;
  @Mock private Info taskInfo;

  private SchemaUpgradeClient<?, ?> underTest;

  @RegisterExtension
  LogCapturer logCapturer = LogCapturer.create().captureForType(SchemaUpgradeClientOS.class);

  @BeforeEach
  public void init() {
    when(openSearchClient.getIndexNameService()).thenReturn(indexNameService);
    underTest =
        createSchemaUpgradeClient(
            schemaManager, metadataService, configurationService, openSearchClient);
  }

  @Test
  public void testSuccessfulReindexWithProgressCheck() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "1234567890";

    mockCountResponseFromIndex(index1, 1L);
    mockCountResponseFromIndex(index2, 0L);
    when(openSearchClient.getOpenSearchClient()).thenReturn(mock(ExtendedOpenSearchClient.class));
    when(openSearchClient.getTaskList(any())).thenReturn(ListResponse.listResponseOf(l -> l));
    when(openSearchClient.submitReindexTask(any(ReindexRequest.class)).task()).thenReturn(taskId);

    // the first task response is in progress, the second is successfully complete
    mockReindexStatus(taskId, createStatus(20L, 3L, 3L, 4L));

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
        // then no exceptions are thrown
        .doesNotThrowAnyException();

    // and reindex was executed
    verify(openSearchClient).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  @Disabled
  // TODO check the mocking issue that causes this test to fail
  public void testReindexDetectsPendingReindexAndWaitForIt() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final int numericTaskId = 12345;
    final String taskId = String.valueOf(numericTaskId);

    mockCountResponseFromIndex(index1, 1L);
    mockCountResponseFromIndex(index2, 0L);
    final Info taskInfo = mock(Info.class);
    // TODO The mocking issue is in the two lines below
    Map<String, Info> mockedResponse = getMockedListResponse(taskInfo).tasks();
    when(openSearchClient.getTaskList(any(ListRequest.class)).tasks()).thenReturn(mockedResponse);
    when(taskInfo.id()).thenReturn(Long.valueOf(numericTaskId));
    when(taskInfo.description()).thenReturn(createReindexTaskDescription(index1, index2));

    mockReindexStatus(taskId, createStatus(20L, 3L, 3L, 4L));

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
        // then no exceptions are thrown
        .doesNotThrowAnyException();

    // and the log contains expected entries
    logCapturer.assertContains(
        "Found pending task with id " + taskId + ", will wait for it to finish.");

    // and reindex was never submitted
    verify(openSearchClient, never()).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testReindexSkippedDueToEqualDocCount() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";

    mockCountResponseFromIndex(index1, 1L);
    mockCountResponseFromIndex(index2, 1L);

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
        // then no exceptions are thrown
        .doesNotThrowAnyException();

    logCapturer.assertContains(
        "Found that index ["
            + index2
            + "] already contains the same amount of documents as ["
            + index1
            + "], will skip reindex.");

    // and reindex was never submitted
    verify(openSearchClient, never()).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testFailOnReindexTaskSubmissionError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";

    mockCountResponseFromIndex(index1, 1L);
    mockCountResponseFromIndex(index2, 0L);
    when(openSearchClient.getTaskList(any())).thenReturn(ListResponse.listResponseOf(l -> l));
    given(openSearchClient.submitReindexTask(any(ReindexRequest.class)).task())
        .willAnswer(
            invocation -> {
              throw new IOException();
            });

    // when
    assertThatThrownBy(() -> underTest.reindex(index1, index2))
        // then an exception is thrown
        .isInstanceOf(UpgradeRuntimeException.class);

    // and reindex was submitted
    verify(openSearchClient).submitReindexTask(any(ReindexRequest.class));
  }

  @Test
  public void testFailOnReindexTaskStatusCheckError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String node = "12345";
    final String taskId = "67890";

    mockCountResponseFromIndex(index1, 1L);
    mockCountResponseFromIndex(index2, 0L);
    mockListInfoResponseContainingSourceAndTarget(taskId, node, index1, index2);

    // the task status response contains an error when checking for status
    final GetTasksResponse taskResponseWithError =
        GetTasksResponse.of(
            r ->
                r.completed(true)
                    .task(createInfo(taskId, 20L, 3L, 3L, 4L))
                    .error(
                        ErrorCause.of(
                            e ->
                                e.type("error")
                                    .reason("failed hard")
                                    .stackTrace(
                                        ImmutableMap.of(
                                                "type", "null_pointer_exception",
                                                "reason", "someReason")
                                            .toString()))));

    whenReindexStatusRequest(taskId).thenReturn(taskResponseWithError);

    // when
    final String newReindexTaskId = "54321";
    when(openSearchClient.submitReindexTask(any(ReindexRequest.class)).task())
        .thenReturn(newReindexTaskId);
    mockReindexStatus(newReindexTaskId, createStatus(20L, 3L, 3L, 4L));
    assertThatCode(() -> underTest.reindex(index1, index2))
        // then no exceptions are thrown
        .doesNotThrowAnyException();

    // then we detect the task that cannot be completed
    logCapturer.assertContains(
        "Pending task is not completable, submitting new task for identifier");
    // and a new reindex task is submitted
    verify(openSearchClient).submitReindexTask(any(ReindexRequest.class));
  }

  private Info createInfo(String taskId, long total, long updated, long created, long deleted) {
    return createInfo(taskId, total, updated, created, deleted, "");
  }

  private Info createInfo(
      String taskId, long total, long updated, long created, long deleted, String description) {
    return Info.of(
        i ->
            i.id(Long.parseLong(taskId))
                .action("indices:data/write/reindex")
                .description(description)
                .cancellable(true)
                .headers(Map.of())
                .node("")
                .runningTimeInNanos(0L)
                .startTimeInMillis(0L)
                .type("")
                .status(createStatus(total, updated, created, deleted)));
  }

  private Status createStatus(long total, long updated, long created, long deleted) {
    return Status.of(
        s ->
            s.total(total)
                .updated(updated)
                .created(created)
                .deleted(deleted)
                .batches(0L)
                .noops(0L)
                .requestsPerSecond(1)
                .retries(new Retries.Builder().bulk(0L).search(0L).build())
                .throttledMillis(1000L)
                .throttledUntilMillis(1000L)
                .versionConflicts(0L));
  }

  @SneakyThrows
  private void mockReindexStatus(final String taskId, final Status inProgressStatus) {

    final GetTasksResponse completedResponse =
        GetTasksResponse.of(r -> r.completed(true).task(createInfo(taskId, 20L, 6L, 6L, 8L)));
    GetTasksResponse progressResponse = null;
    if (inProgressStatus != null) {
      progressResponse =
          GetTasksResponse.of(t -> t.completed(false).task(createInfo(taskId, 0L, 0L, 0L, 0L)));
    }
    OngoingStubbing<GetTasksResponse> responseOngoingStubbing = whenReindexStatusRequest(taskId);
    if (progressResponse != null) {
      responseOngoingStubbing = responseOngoingStubbing.thenReturn(progressResponse);
    }
    responseOngoingStubbing.thenReturn(completedResponse);
  }

  private void mockListInfoResponseContainingSourceAndTarget(
      final String taskId, String node, final String sourceIndex, final String targetIndex)
      throws IOException {
    when(taskInfo.description()).thenReturn(createReindexTaskDescription(sourceIndex, targetIndex));
    when(taskInfo.id()).thenReturn(Long.valueOf(taskId));
    ListResponse mockedListResponse = getMockedListResponse(taskInfo);
    when(openSearchClient.getTaskList(any())).thenReturn(mockedListResponse);
  }

  private ListResponse getMockedListResponse(Info taskInfo) {
    return new ListResponse.Builder().tasks(Map.of("" + this.taskInfo.id(), this.taskInfo)).build();
  }

  private static String createReindexTaskDescription(
      final String sourceIndexName, final String targetIndexName) {
    return SchemaUpgradeClient.createReIndexRequestDescription(
        List.of(sourceIndexName), targetIndexName);
  }

  @SneakyThrows
  private OngoingStubbing<GetTasksResponse> whenReindexStatusRequest(final String taskId) {
    return when(openSearchClient.getRichOpenSearchClient().task().taskWithRetries(taskId));
  }

  @SneakyThrows
  private void mockCountResponseFromIndex(final String indexName, final long count) {
    when(openSearchClient.countWithoutPrefix(matches(indexName))).thenAnswer(a -> count);
  }

  private Response createOsResponse(GetTasksResponse taskResponse) throws IOException {
    final Response mockedReindexResponse = mock(Response.class);

    final HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent())
        .thenReturn(new ByteArrayInputStream(OPTIMIZE_MAPPER.writeValueAsBytes(taskResponse)));
    when(mockedReindexResponse.getEntity()).thenReturn(httpEntity);

    return mockedReindexResponse;
  }
}
