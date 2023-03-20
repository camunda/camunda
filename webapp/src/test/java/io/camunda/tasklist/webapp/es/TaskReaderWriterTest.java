/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskReaderWriterTest {

  private static final List<String> FIELD_NAMES =
      List.of(
          "id",
          "name",
          "processName",
          "assignee",
          "creationTime",
          "taskState",
          "sortValues",
          "isFirst",
          "__typename");

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  @Mock private RestHighLevelClient esClient;

  @Spy private TaskTemplate taskTemplate = new TaskTemplate();

  @Spy private ObjectMapper objectMapper = CommonUtils.getObjectMapper();

  @InjectMocks private TaskReaderWriter instance;

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(taskTemplate, "tasklistProperties", new TasklistProperties());
  }

  @ParameterizedTest
  @CsvSource({
    "CREATED,tasklist-task-8.2.3_",
    "COMPLETED,tasklist-task-8.2.3_alias",
    "CANCELED,tasklist-task-8.2.3_alias"
  })
  void getTasksForDifferentStates(TaskState taskState, String expectedIndexName) throws Exception {
    // Given
    final TaskQueryDTO taskQuery = new TaskQueryDTO().setPageSize(50).setState(taskState);

    final SearchResponse mockedResponse = mock();
    when(esClient.search(searchRequestCaptor.capture(), eq(RequestOptions.DEFAULT)))
        .thenReturn(mockedResponse);

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    final SearchHit mockedHit = mock();
    when(mockedHints.getHits()).thenReturn(new SearchHit[] {mockedHit});

    when(mockedHit.getSourceAsString()).thenReturn(getTaskExampleAsString(taskState));

    // When
    final List<TaskDTO> result = instance.getTasks(taskQuery, FIELD_NAMES);

    // Then
    assertEquals(1, searchRequestCaptor.getValue().indices().length, "indices count is wrong");
    assertEquals(expectedIndexName, searchRequestCaptor.getValue().indices()[0], "index is wrong");
    assertEquals(1, result.size());
  }

  private static String getTaskExampleAsString(TaskState taskState) {
    return "{\n"
        + "  \"id\": \"123456789\",\n"
        + "  \"key\": 123456789,\n"
        + "  \"partitionId\": 2,\n"
        + "  \"bpmnProcessId\": \"bigFormProcess\",\n"
        + "  \"processDefinitionId\": \"00000000000\",\n"
        + "  \"flowNodeBpmnId\": \"Activity_0aaaaa\",\n"
        + "  \"flowNodeInstanceId\": \"11111111111\",\n"
        + "  \"processInstanceId\": \"2222222222\",\n"
        + "  \"creationTime\": \"2023-01-01T00:00:02.523+0200\",\n"
        + "  \"completionTime\": null,\n"
        + "  \"state\": \""
        + taskState.toString()
        + "\",\n"
        + "  \"assignee\": null,\n"
        + "  \"candidateGroups\": null,\n"
        + "  \"formKey\": \"camunda-forms:bpmn:userTaskForm_1111111\"\n"
        + "}";
  }
}
