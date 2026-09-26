/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.util.TaskVariableSearchUtil;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

@ExtendWith(MockitoExtension.class)
public class TaskStoreOpenSearchTest {

  @Captor private ArgumentCaptor<SearchRequest.Builder> searchRequestCaptor;

  @Mock private TenantAwareOpenSearchClient tenantAwareClient;

  @Mock private OpenSearchClient osClient;

  @Spy private final TaskTemplate taskTemplate = new TaskTemplate("test", true);

  @Spy
  private final SnapshotTaskVariableTemplate taskVariableTemplate =
      new SnapshotTaskVariableTemplate("test", true);

  @Spy private final TasklistProperties tasklistProperties = new TasklistProperties();

  @Mock private VariableStore variableStoreOpensearch;

  @Mock private TaskVariableSearchUtil taskVariableSearchUtil;

  @InjectMocks private TaskStoreOpenSearch instance;

  @ParameterizedTest
  @CsvSource({
    "CREATED,test-tasklist-task-,_",
    "COMPLETED,test-tasklist-task-,_alias",
    "CANCELED,test-tasklist-task-,_alias"
  })
  void getTasksForDifferentStates(
      final TaskState taskState, final String expectedIndexPrefix, final String expectedIndexSuffix)
      throws Exception {
    // Given
    final TaskQuery taskQuery = new TaskQuery().setPageSize(50).setState(taskState);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.search(searchRequestCaptor.capture(), any(Class.class)))
        .thenReturn(mockedResponse);

    final Hit mockedHit = mock();
    when(mockedHit.source()).thenReturn(getTaskEntity(taskState));

    final HitsMetadata mockedHits = mock();
    when(mockedResponse.hits()).thenReturn(mockedHits);
    when(mockedHits.hits()).thenReturn(List.of(mockedHit));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    final SearchRequest searchRequest = searchRequestCaptor.getValue().build();
    assertThat(searchRequest.index())
        .singleElement(as(STRING))
        .satisfies(
            index -> {
              assertThat(index).startsWith(expectedIndexPrefix);
              assertThat(index).endsWith(expectedIndexSuffix);
            });
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
    verify(tenantAwareClient).search(searchRequestCaptor.capture(), eq(TaskEntity.class));
  }

  @Test
  void queryTasksWithProvidedTenantIds() throws IOException, IOException {
    final TaskQuery taskQuery =
        new TaskQuery()
            .setTenantIds(new String[] {"tenant_a", "tenant_b"})
            .setPageSize(50)
            .setState(TaskState.CREATED);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.searchByTenantIds(
            any(), eq(TaskEntity.class), eq(Set.of("tenant_a", "tenant_b"))))
        .thenReturn(mockedResponse);

    final Hit mockedHit = mock();
    when(mockedHit.source()).thenReturn(getTaskEntity(TaskState.CREATED));

    final HitsMetadata mockedHits = mock();
    when(mockedHits.hits()).thenReturn(List.of(mockedHit));
    when(mockedResponse.hits()).thenReturn(mockedHits);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    verify(tenantAwareClient, never()).search(any(), any(Class.class));
    assertThat(result).hasSize(1);
  }

  @Test
  void getTaskShouldWrapOpenSearchExceptionInTasklistRuntimeException() throws IOException {
    // Given a server-side OS failure (e.g. "all shards failed") surfaces as an
    // OpenSearchException, which is a RuntimeException rather than an IOException.
    when(tenantAwareClient.search(any(), eq(TaskEntity.class)))
        .thenThrow(mock(OpenSearchException.class));

    // When / Then it must be mapped to a TasklistRuntimeException (handled at WARN) instead of
    // leaking to the generic exception handler (logged at ERROR). See issue #35823.
    assertThatThrownBy(() -> instance.getTask("123456789"))
        .isInstanceOf(TasklistRuntimeException.class);
  }

  @Test
  void toVariablesMapKeepsFirstValueOnDuplicateFilterName() {
    // Given two filter entries for the same variable name (e.g. "businessKey") with
    // different values -- this used to throw IllegalStateException from the unguarded
    // Collectors.toMap() and crash the whole task search request. See #63182.
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("businessKey").setValue("second-value").setOperator("eq")
    };

    // When
    final Map<String, String> variablesMap =
        TaskStoreOpenSearch.toVariablesMap(taskVariablesFilter);

    // Then it does not throw and keeps the first value for the repeated name
    assertThat(variablesMap)
        .containsExactlyInAnyOrderEntriesOf(Map.of("businessKey", "first-value"));
  }

  @Test
  void toVariablesMapKeepsAllValuesForUniqueFilterNames() {
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("otherVariable").setValue("second-value").setOperator("eq")
    };

    final Map<String, String> variablesMap =
        TaskStoreOpenSearch.toVariablesMap(taskVariablesFilter);

    assertThat(variablesMap)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("businessKey", "first-value", "otherVariable", "second-value"));
  }

  @Test
  void getTasksAppliesKeepFirstConsistentlyToActiveTaskMatchingForDuplicateFilterNames()
      throws Exception {
    // Given two "businessKey" filters with different values, and a process instance whose
    // businessKey is set to only the first value. Pre-fix, getTasksContainsVarNameAndValue
    // queried getProcessInstanceKeysWithMatchingVars with BOTH duplicate entries, intersecting
    // the per-entry matches and finding no process instance at all -- even this one, which does
    // have the first value. The dedup fix (keep first value) must apply to this branch too, not
    // only to retrieveTaskIdByProcessInstanceId. See #63182.
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("businessKey").setValue("second-value").setOperator("eq")
    };
    final TaskQuery taskQuery =
        new TaskQuery()
            .setPageSize(50)
            .setState(TaskState.CREATED)
            .setTaskVariables(taskVariablesFilter);

    // Only the deduplicated single-entry filter (businessKey=first-value) matches a process
    // instance; the un-deduplicated two-entry filter would find nothing.
    when(variableStoreOpensearch.getProcessInstanceKeysWithMatchingVars(anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              final List<String> names = invocation.getArgument(0);
              final List<String> values = invocation.getArgument(1);
              return names.equals(List.of("businessKey")) && values.equals(List.of("first-value"))
                  ? List.of(100L)
                  : Collections.emptyList();
            });
    when(taskVariableSearchUtil.getTaskIdsContainingVariables(anyList(), anyMap()))
        .thenReturn(List.of("task-active-1"));

    // No completed tasks match in this scenario.
    final SearchResponse emptyCompletedResponse = mock();
    final HitsMetadata emptyCompletedHits = mock();
    when(emptyCompletedResponse.hits()).thenReturn(emptyCompletedHits);
    when(emptyCompletedHits.hits()).thenReturn(List.of());
    when(osClient.search(any(SearchRequest.class), eq(SnapshotTaskVariableEntity.class)))
        .thenReturn(emptyCompletedResponse);

    // The active-task lookup scrolls TaskEntity documents via osClient.
    final SearchResponse activeTaskResponse = mock();
    final HitsMetadata activeTaskHits = mock();
    final Hit activeTaskHit = mock();
    when(activeTaskHit.source()).thenReturn(getTaskEntity(TaskState.CREATED));
    when(activeTaskResponse.hits()).thenReturn(activeTaskHits);
    when(activeTaskHits.hits()).thenReturn(List.of(activeTaskHit));
    when(activeTaskResponse.scrollId()).thenReturn("scroll-active-1");
    when(osClient.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(activeTaskResponse);

    final ScrollResponse emptyTaskEntityScroll = mock();
    final HitsMetadata emptyTaskEntityScrollHits = mock();
    when(emptyTaskEntityScroll.hits()).thenReturn(emptyTaskEntityScrollHits);
    when(emptyTaskEntityScrollHits.hits()).thenReturn(List.of());
    when(osClient.scroll(any(ScrollRequest.class), eq(TaskEntity.class)))
        .thenReturn(emptyTaskEntityScroll);

    // The final task listing goes through tenantAwareClient.
    final SearchResponse finalListingResponse = mock();
    final HitsMetadata finalListingHits = mock();
    final Hit finalListingHit = mock();
    when(finalListingHit.source()).thenReturn(getTaskEntity(TaskState.CREATED));
    when(finalListingResponse.hits()).thenReturn(finalListingHits);
    when(finalListingHits.hits()).thenReturn(List.of(finalListingHit));
    when(tenantAwareClient.search(any(), eq(TaskEntity.class))).thenReturn(finalListingResponse);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(result).hasSize(1);
  }

  @Test
  void getTasksAppliesKeepFirstConsistentlyToCompletedTaskMatchingForDuplicateFilterNames()
      throws Exception {
    // Given two "businessKey" filters with different values, and a completed task whose only
    // recorded businessKey is the first value. Pre-fix, getTasksIdsCompletedWithMatchingVars ran
    // once per duplicate filter entry and intersected the per-entry task-id sets, so this task
    // would incorrectly disappear instead of being kept per the documented keep-first behavior.
    // See #63182.
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("businessKey").setValue("second-value").setOperator("eq")
    };
    final TaskQuery taskQuery =
        new TaskQuery()
            .setPageSize(50)
            .setState(TaskState.COMPLETED)
            .setTaskVariables(taskVariablesFilter);

    // No active (created) process instances match in this scenario.
    when(variableStoreOpensearch.getProcessInstanceKeysWithMatchingVars(anyList(), anyList()))
        .thenReturn(Collections.emptyList());
    when(taskVariableSearchUtil.getTaskIdsContainingVariables(anyList(), anyMap()))
        .thenReturn(Collections.emptyList());

    final SearchResponse completedResponse = mock();
    final HitsMetadata completedHits = mock();
    final Hit completedHit = mock();
    when(completedHit.source())
        .thenReturn(new SnapshotTaskVariableEntity().setTaskId("task-completed-1"));
    when(completedResponse.hits()).thenReturn(completedHits);
    when(completedHits.hits()).thenReturn(List.of(completedHit));
    when(completedResponse.scrollId()).thenReturn("scroll-completed-1");
    when(osClient.search(any(SearchRequest.class), eq(SnapshotTaskVariableEntity.class)))
        .thenReturn(completedResponse);

    final ScrollResponse emptyCompletedScroll = mock();
    final HitsMetadata emptyCompletedScrollHits = mock();
    when(emptyCompletedScroll.hits()).thenReturn(emptyCompletedScrollHits);
    when(emptyCompletedScrollHits.hits()).thenReturn(List.of());
    when(osClient.scroll(any(ScrollRequest.class), eq(SnapshotTaskVariableEntity.class)))
        .thenReturn(emptyCompletedScroll);

    final SearchResponse finalListingResponse = mock();
    final HitsMetadata finalListingHits = mock();
    final Hit finalListingHit = mock();
    when(finalListingHit.source()).thenReturn(getTaskEntity(TaskState.COMPLETED));
    when(finalListingResponse.hits()).thenReturn(finalListingHits);
    when(finalListingHits.hits()).thenReturn(List.of(finalListingHit));
    when(tenantAwareClient.search(any(), eq(TaskEntity.class))).thenReturn(finalListingResponse);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then the completed-task lookup ran exactly once, for the deduplicated single filter entry
    // -- not once per duplicate filter entry.
    verify(osClient, times(1))
        .search(any(SearchRequest.class), eq(SnapshotTaskVariableEntity.class));
    assertThat(result).hasSize(1);
  }

  private static TaskEntity getTaskEntity(final TaskState taskState) {
    final TaskEntity taskEntity = new TaskEntity();
    taskEntity.setId("123456789");
    taskEntity.setKey(123456789L);
    taskEntity.setPartitionId(2);
    taskEntity.setBpmnProcessId("bigFormProcess");
    taskEntity.setProcessDefinitionId("00000000000");
    taskEntity.setFlowNodeBpmnId("Activity_0aaaaa");
    taskEntity.setFlowNodeInstanceId("11111111111");
    taskEntity.setProcessInstanceId("2222222222");
    taskEntity.setCreationTime(OffsetDateTime.parse("2023-01-01T00:00:02.523+02:00"));
    taskEntity.setCompletionTime(null);
    taskEntity.setState(taskState);
    taskEntity.setAssignee(null);
    taskEntity.setCandidateGroups(null);
    taskEntity.setFormKey("camunda-forms:bpmn:userTaskForm_1111111");
    taskEntity.setImplementation(TaskImplementation.JOB_WORKER);
    return taskEntity;
  }
}
