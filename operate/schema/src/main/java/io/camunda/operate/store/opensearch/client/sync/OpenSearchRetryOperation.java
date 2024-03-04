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
package io.camunda.operate.store.opensearch.client.sync;

import static io.camunda.operate.util.ExceptionHelper.withIOException;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.opensearch.client.OpenSearchFailedShardsException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;
import org.slf4j.Logger;

public abstract class OpenSearchRetryOperation extends OpenSearchSyncOperation {
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;

  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  private final int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  private final int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;

  public OpenSearchRetryOperation(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  protected <T> T executeWithRetries(CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  protected <T> T executeWithRetries(String operationName, CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  protected <T> T executeWithRetries(
      String operationName, CheckedSupplier<T> supplier, Predicate<T> retryPredicate) {
    return executeWithGivenRetries(numberOfRetries, operationName, supplier, retryPredicate);
  }

  protected <T> T executeWithGivenRetries(
      int retries, String operationName, CheckedSupplier<T> operation, Predicate<T> predicate) {
    try {
      final RetryPolicy<T> retryPolicy =
          new RetryPolicy<T>()
              .handle(
                  IOException.class,
                  OpenSearchException.class,
                  OpenSearchFailedShardsException.class)
              .withDelay(Duration.ofSeconds(delayIntervalInSeconds))
              .withMaxAttempts(retries)
              .onRetry(
                  e ->
                      logger.info(
                          "Retrying #{} {} due to {}",
                          e.getAttemptCount(),
                          operationName,
                          e.getLastFailure()))
              .onAbort(e -> logger.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e ->
                      logger.error(
                          "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      if (predicate != null) {
        retryPolicy.handleResultIf(predicate);
      }
      return Failsafe.with(retryPolicy).get(operation);
    } catch (Exception e) {
      throw new OperateRuntimeException(
          "Couldn't execute operation "
              + operationName
              + " on opensearch for "
              + retries
              + " attempts with "
              + delayIntervalInSeconds
              + " seconds waiting.",
          e);
    }
  }

  protected GetTasksResponse task(String id) throws IOException {
    return openSearchClient.tasks().get(t -> t.taskId(id));
  }

  protected Map<String, Info> tasksWithActions(List<String> actions) throws IOException {
    return openSearchClient.tasks().list(l -> l.actions(actions)).tasks();
  }

  protected GetTasksResponse waitTaskCompletion(String taskId) {
    final String[] taskIdParts = taskId.split(":");
    final String nodeId = taskIdParts[0];
    final long id = Long.parseLong(taskIdParts[1]);
    return executeWithGivenRetries(
        Integer.MAX_VALUE,
        "GetTaskInfo{" + nodeId + "},{" + id + "}",
        () -> {
          checkTaskErrorsOrFailures(nodeId, (int) id);
          return task(taskId);
        },
        this::needsToPollAgain);
  }

  private void checkTaskErrorsOrFailures(final String node, final Integer id) throws IOException {
    final GetTasksResponse tasks = withIOException(() -> task(node + ":" + id));

    if (tasks != null) {
      checkForErrors(tasks);
      checkForFailures(tasks);
    }
  }

  private void checkForErrors(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      throw new OperateRuntimeException(taskResponse.error().reason());
    }
  }

  private void checkForFailures(final GetTasksResponse taskResponse) {
    if (taskResponse.response().failures() != null) {
      throw new OperateRuntimeException(taskResponse.response().failures().get(0));
    }
  }

  private boolean needsToPollAgain(final GetTasksResponse taskResponse) {
    var r = taskResponse.response();
    var allTasksExecuted = r.total() == r.created() + r.updated() + r.deleted();
    return !(taskResponse.completed() && allTasksExecuted);
  }
}
