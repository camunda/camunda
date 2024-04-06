/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.views.TaskSearchView;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskStoreElasticSearchTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  @Mock private TenantAwareElasticsearchClient tenantAwareClient;

  @Spy private TaskTemplate taskTemplate = new TaskTemplate();

  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @InjectMocks private TaskStoreElasticSearch instance;

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(taskTemplate, "tasklistProperties", new TasklistProperties());
  }

  @ParameterizedTest
  @CsvSource({
    "CREATED,tasklist-task-,_",
    "COMPLETED,tasklist-task-,_alias",
    "CANCELED,tasklist-task-,_alias"
  })
  void getTasksForDifferentStates(
      TaskState taskState, String expectedIndexPrefix, String expectedIndexSuffix)
      throws Exception {
    // Given
    final TaskQuery taskQuery = new TaskQuery().setPageSize(50).setState(taskState);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.search(searchRequestCaptor.capture())).thenReturn(mockedResponse);

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    final SearchHit mockedHit = mock();
    when(mockedHints.getHits()).thenReturn(new SearchHit[] {mockedHit});

    when(mockedHit.getSourceAsString()).thenReturn(getTaskExampleAsString(taskState));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(searchRequestCaptor.getValue().indices())
        .singleElement(as(STRING))
        .satisfies(
            index -> {
              assertThat(index).startsWith(expectedIndexPrefix);
              assertThat(index).endsWith(expectedIndexSuffix);
            });
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
    verify(tenantAwareClient).search(any());
  }

  @Test
  void queryTasksWithProvidedTenantIds() throws IOException {
    final TaskQuery taskQuery =
        new TaskQuery()
            .setTenantIds(new String[] {"tenant_a", "tenant_b"})
            .setPageSize(50)
            .setState(TaskState.CREATED);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.searchByTenantIds(any(), eq(Set.of("tenant_a", "tenant_b"))))
        .thenReturn(mockedResponse);

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    final SearchHit mockedHit = mock();
    when(mockedHints.getHits()).thenReturn(new SearchHit[] {mockedHit});

    when(mockedHit.getSourceAsString()).thenReturn(getTaskExampleAsString(TaskState.CREATED));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    verify(tenantAwareClient, never()).search(any());
    assertThat(result).hasSize(1);
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
        + "  \"formKey\": \"camunda-forms:bpmn:userTaskForm_1111111\",\n"
        + "  \"implementation\": \"JOB_WORKER\"\n"
        + "}";
  }
}
