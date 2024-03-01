/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.TaskStore;
import io.camunda.operate.store.elasticsearch.dao.response.TaskResponse;
import io.camunda.operate.util.Either;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.tasks.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTaskStore implements TaskStore {

  public static final String ID = "id";
  public static final String REASON = "reason";
  public static final String CAUSE = "cause";
  public static final String CREATED = "created";
  public static final String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";
  public static final String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  public static final String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  private static final String TASKS_ENDPOINT = "_tasks";
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTaskStore.class);
  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  public Either<IOException, TaskResponse> getTaskResponse(final String taskId) {
    try {
      final var request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
      final var response = esClient.getLowLevelClient().performRequest(request);
      final var taskResponse =
          objectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
      return Either.right(taskResponse);
    } catch (IOException e) {
      return Either.left(e);
    }
  }

  public void checkForErrorsOrFailures(final TaskResponse taskResponse) {
    checkForErrors(taskResponse);
    checkForFailures(taskResponse);
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex, final String toIndex)
      throws IOException {
    if (fromIndex == null || toIndex == null) {
      return List.of();
    }
    return getReindexTasks().stream()
        .filter(
            taskInfo ->
                descriptionContainsReindexFromTo(taskInfo.getDescription(), fromIndex, toIndex))
        .map(this::toTaskId)
        .toList();
  }

  private String toTaskId(TaskInfo taskInfo) {
    return String.format("%s:%s", taskInfo.getTaskId().getNodeId(), taskInfo.getTaskId().getId());
  }

  private boolean descriptionContainsReindexFromTo(
      final String description, final String fromIndex, final String toIndex) {
    return description != null
        && description.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex)
        && description.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);
  }

  private List<TaskInfo> getReindexTasks() throws IOException {
    var response =
        esClient
            .tasks()
            .list(
                new ListTasksRequest().setActions(TASK_ACTION_INDICES_REINDEX).setDetailed(true),
                RequestOptions.DEFAULT);
    return response.getTasks();
  }

  private void checkForErrors(final TaskResponse taskResponse) {
    if (taskResponse != null && taskResponse.getError() != null) {
      final var error = taskResponse.getError();
      logger.error("Task status contains error: " + error);
      throw new OperateRuntimeException(error.getReason());
    }
  }

  private void checkForFailures(final TaskResponse taskStatus) {
    if (taskStatus != null && taskStatus.getResponseDetails() != null) {
      final var taskResponse = taskStatus.getResponseDetails();
      final var failures = taskResponse.getFailures();
      if (!failures.isEmpty()) {
        final Map<String, Object> failure = (Map<String, Object>) failures.get(0);
        final Map<String, String> cause = (Map<String, String>) failure.get(CAUSE);
        throw new OperateRuntimeException(cause.get(REASON));
      }
    }
  }

  public boolean needsToPollAgain(final Optional<TaskResponse> maybeTaskResponse) {
    return maybeTaskResponse.isEmpty()
        || maybeTaskResponse.filter(tr -> !tr.isCompleted()).isPresent();
  }
}
