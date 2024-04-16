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
package io.camunda.tasklist.os;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchInternalTask {

  public static final String ERROR = "error";
  public static final String REASON = "reason";
  public static final String RESPONSE = "response";
  public static final String FAILURES = "failures";
  public static final String CAUSE = "cause";
  public static final String SYSTEM_TASKS_INDEX = ".tasks";
  public static final String TOTAL = "total";
  public static final String CREATED = "created";
  public static final String UPDATED = "updated";
  public static final String DELETED = "deleted";
  public static final String TASK_ACTION = "task.action";
  public static final String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";
  public static final String TASK = "task";
  public static final String DESCRIPTION = "description";
  public static final String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  public static final String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  public static final String NODE = "node";
  public static final int MAX_TASKS_ENTRIES = 2_000;
  public static final String ID = "id";

  @Autowired private OpenSearchClient openSearchClient;

  public void checkForErrorsOrFailures(final GetTasksResponse tasks) throws IOException {
    if (tasks != null) {
      checkForErrors(tasks);
      checkForFailures(tasks);
    }
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex, final String toIndex)
      throws IOException {
    if (!systemTaskIndexExists() || fromIndex == null || toIndex == null) {
      return List.of();
    }

    return getReindexTasks().stream()
        .filter(taskState -> descriptionContainsReindexFromTo(taskState, fromIndex, toIndex))
        .map(this::toTaskId)
        .toList();
  }

  private String toTaskId(Map<String, Object> taskState) {
    return String.format("%s:%s", taskState.get(NODE), taskState.get(ID));
  }

  private boolean descriptionContainsReindexFromTo(
      final Map<String, Object> taskState, final String fromIndex, final String toIndex) {
    final String desc = (String) taskState.get(DESCRIPTION);
    return desc != null
        && desc.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex)
        && desc.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);
  }

  private List<Map<String, Object>> getReindexTasks() throws IOException {
    final SearchResponse<Map> searchResponse =
        openSearchClient.search(
            s ->
                s.index(SYSTEM_TASKS_INDEX)
                    .query(
                        q ->
                            q.term(
                                term ->
                                    term.field(TASK_ACTION)
                                        .value(FieldValue.of(TASK_ACTION_INDICES_REINDEX))))
                    .size(MAX_TASKS_ENTRIES),
            Map.class);

    return searchResponse.hits().hits().stream()
        .map(h -> (Map<String, Object>) h.source().get(TASK))
        .toList();
  }

  private boolean systemTaskIndexExists() throws IOException {
    return openSearchClient.indices().exists(e -> e.index(SYSTEM_TASKS_INDEX)).value();
  }

  private void checkForErrors(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      throw new TasklistRuntimeException(taskResponse.error().reason());
    }
  }

  private void checkForFailures(final GetTasksResponse taskResponse) {
    if (!CollectionUtils.isEmpty(taskResponse.response().failures())) {
      throw new TasklistRuntimeException(taskResponse.response().failures().get(0));
    }
  }

  public boolean needsToPollAgain(final GetTasksResponse taskResponse) {
    if (taskResponse == null) {
      return false;
    }
    final Status taskStatus = getTaskStatus(taskResponse);
    final long total = taskStatus.total();
    final long created = taskStatus.created();
    final long updated = taskStatus.updated();
    final long deleted = taskStatus.deleted();
    return !taskResponse.completed() || (created + updated + deleted != total);
  }

  private Status getTaskStatus(final GetTasksResponse taskResponse) {
    return taskResponse.task().status();
  }

  public long getTotal(final GetTasksResponse taskResponse) {
    return getTaskStatus(taskResponse).total();
  }
}
