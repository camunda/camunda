/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.util;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ibm.icu.text.Collator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.GroupUser;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.statistics.response.GlobalJobStatistics;
import io.camunda.client.impl.search.filter.DecisionDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.DecisionRequirementsFilterImpl;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;

/**
 * This class provides several static methods to facilitate common operations such as deploying
 * resources, starting process instances, and waiting for certain conditions to be met. These
 * methods are designed to be used in integration tests to reduce code duplication and improve
 * readability.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CamundaClient camundaClient = ...; // initialize your Camunda client
 *
 * // Deploy a resource
 * DeploymentEvent deploymentEvent = deployResource(camundaClient, "path/to/resource.bpmn");
 *
 * // Start a process instance
 * ProcessInstanceEvent processInstanceEvent = startProcessInstance(camundaClient, "processId");
 *
 * // Wait for process instances to start
 * waitForProcessInstancesToStart(camundaClient, 1);
 *
 * // Assert sorting of query results
 * SearchQueryResponse<Incident> resultAsc = ...; // get ascending sorted result
 * SearchQueryResponse<Incident> resultDesc = ...; // get descending sorted result
 * assertSorted(resultAsc, resultDesc, Incident::getCreationTime);
 * }</pre>
 */
public final class TestHelper {

  public static final String VAR_TEST_SCOPE_ID = "testScopeId";
  public static final String DEFAULT_TENANT_ID = "<default>";

  public static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .execute();
  }

  /**
   * Deploys a process and waits for it to be available in the secondary database.
   *
   * @param client ... CamundaClient
   * @param processDefinition ... BpmnModelInstance of the process definition
   * @return the deployed process
   */
  public static Process deployProcessAndWaitForIt(
      final CamundaClient client,
      final BpmnModelInstance processDefinition,
      final String resourceName) {
    final var event =
        client
            .newDeployResourceCommand()
            .addProcessModel(processDefinition, resourceName)
            .execute()
            .getProcesses()
            .getFirst();

    // sync with secondary database
    waitForProcessesToBeDeployed(
        client, f -> f.processDefinitionKey(event.getProcessDefinitionKey()), 1);

    return event;
  }

  /**
   * Deploys a process and waits for it to be available in the secondary database.
   *
   * @param client ... CamundaClient
   * @param classpath ... e.g. process/migration-process_v1.bpmn
   * @return
   */
  public static Process deployProcessAndWaitForIt(
      final CamundaClient client, final String classpath) {
    final var event = deployResource(client, classpath).getProcesses().getFirst();

    // sync with secondary database
    waitForProcessesToBeDeployed(
        client, f -> f.processDefinitionKey(event.getProcessDefinitionKey()), 1);

    return event;
  }

  public static DeploymentEvent deployResource(
      final CamundaClient camundaClient,
      final BpmnModelInstance processModel,
      final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processModel, resourceName)
        .execute();
  }

  public static DeploymentEvent deployResourceForTenant(
      final CamundaClient camundaClient,
      final BpmnModelInstance processModel,
      final String resourceName,
      final String tenantId) {
    return camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processModel, resourceName)
        .tenantId(tenantId)
        .execute();
  }

  public static DeploymentEvent deployResourceForTenant(
      final CamundaClient camundaClient, final String resourceName, final String tenantId) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenantId)
        .execute();
  }

  public static void createTenant(
      final CamundaClient client,
      final String tenantId,
      final String tenantName,
      final String... usernames) {
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantName).execute();
    for (final var username : usernames) {
      client.newAssignUserToTenantCommand().username(username).tenantId(tenantId).execute();
    }
  }

  public static void deleteTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newDeleteTenantCommand(tenant).execute();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .execute();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final long processDefinitionKey) {
    return camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .execute();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final long processDefinitionKey, final String tenantId) {
    return camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tenantId(tenantId)
        .execute();
  }

  /**
   * Send complete job command for a given job key.
   *
   * @param camundaClient the Camunda client
   * @param jobKey the job key to complete
   */
  public static void completeJob(final CamundaClient camundaClient, final long jobKey) {
    camundaClient.newCompleteCommand(jobKey).execute();
  }

  /**
   * Cancels the given process instance and waits for it to be terminated.
   *
   * @param camundaClient the Camunda client
   * @param instance the process instance event
   */
  public static void cancelInstance(
      final CamundaClient camundaClient, final ProcessInstanceEvent instance) {
    camundaClient.newCancelInstanceCommand(instance.getProcessInstanceKey()).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, instance.getProcessInstanceKey());
  }

  public static ProcessInstanceEvent startProcessInstanceForTenant(
      final CamundaClient camundaClient, final String bpmnProcessId, final String tenant) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .tenantId(tenant)
        .execute();
  }

  public static CorrelateMessageResponse startProcessInstanceWithMessage(
      final CamundaClient camundaClient, final String messageName) {
    return camundaClient
        .newCorrelateMessageCommand()
        .messageName(messageName)
        .withoutCorrelationKey()
        .execute();
  }

  public static ProcessInstanceEvent startProcessInstanceWithTags(
      final CamundaClient camundaClient, final String bpmnProcessId, final Set<String> tags) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .tags(tags)
        .execute();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.execute();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient,
      final String bpmnProcessId,
      final Map<String, Object> variables) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 cmd =
        camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (variables != null && !variables.isEmpty()) {
      cmd.variables(variables);
    }
    return cmd.execute();
  }

  /**
   * Helper method to wait for items in a SearchRequest to be available, supporting pagination.
   *
   * <p>This is useful for cases where a large number of items are expected, and they might not all
   * be available at once. The method will keep fetching items in batches until the expected count
   * is reached or the timeout is exceeded.
   *
   * <p>To use it, call it with a search function that takes a Consumer<SearchRequestPage>. This
   * provides you with the pagination parameters to use in the search request. Just pass the
   * SearchRequestPage page to the page() method of your search request. The method will handle the
   * pagination and counting for you.
   *
   * <p>For example, to wait for 1000 element instances of a process to be available, you could do:
   *
   * <pre>
   *   waitForItemsPaginated(
   *     "wait until element instances of process 'myProcess' are available",
   *     1000,
   *     page ->
   *         camundaClient
   *             .newElementInstanceSearchRequest()
   *             .filter(f -> f.processDefinitionId("myProcess"))
   *             .page(page)
   *             .execute());
   * </pre>
   *
   * @param timeoutMessage The message to display when the timeout {@code TIMEOUT_DATA_AVAILABILITY}
   *     is exceeded.
   * @param expectedCount The expected number of items to be available. The method will keep
   *     fetching items until this count is reached or the timeout is exceeded.
   * @param searchFunction A function that is provided with a SearchRequest Page set up by this
   *     method to handle pagination. The function should execute the search request and return the
   *     SearchResponse. The method will call this function repeatedly with updated pagination
   *     parameters until the expected count of items is reached or the timeout is exceeded.
   * @param <T> The type of items being searched for (e.g., ProcessInstance, ElementInstance, etc.).
   *     Can typically be inferred from the searchFunction parameter.
   * @return The list of items collected during the wait operation.
   */
  private static <T> List<T> waitForItemsPaginated(
      final String timeoutMessage,
      final int expectedCount,
      final Function<Consumer<SearchRequestPage>, SearchResponse<T>> searchFunction) {
    final var collectedItems = new ArrayList<T>();
    Awaitility.await(timeoutMessage)
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .until(
            () -> {
              final int currentCollected = collectedItems.size();
              if (currentCollected >= expectedCount) {
                return true;
              }
              final int limit = Math.min(100, expectedCount - currentCollected);
              final var response = searchFunction.apply(p -> p.from(currentCollected).limit(limit));
              final var items = response.items();

              if (!items.isEmpty()) {
                collectedItems.addAll(items);
              }

              return collectedItems.size() >= expectedCount;
            });
    return collectedItems;
  }

  public static void waitForProcessInstancesToStart(
      final CamundaClient camundaClient, final int expectedProcessInstances) {
    waitForProcessInstancesToStart(camundaClient, f -> {}, expectedProcessInstances);
  }

  public static void waitForProcessInstancesToStart(
      final CamundaClient camundaClient,
      final Consumer<ProcessInstanceFilter> filter,
      final int expectedProcessInstances) {
    waitForItemsPaginated(
        "should start process instances and import in Operate",
        expectedProcessInstances,
        page ->
            camundaClient.newProcessInstanceSearchRequest().filter(filter).page(page).execute());
  }

  /**
   * Starts a process instance with a process variable {@link #VAR_TEST_SCOPE_ID} for scope.
   *
   * @param camundaClient
   * @param scopeId ... some unique id for the test case
   */
  public static ProcessInstanceEvent startScopedProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId, final String scopeId) {
    return startScopedProcessInstance(
        camundaClient, bpmnProcessId, scopeId, Map.of(VAR_TEST_SCOPE_ID, scopeId));
  }

  /**
   * Starts a process instance with a process variable {@link #VAR_TEST_SCOPE_ID} for scope and
   * additional variables.
   *
   * @param camundaClient
   * @param processDefinitionKey ... the process definition key
   * @param scopeId ... some unique id for the test case
   * @param variables ... additional variables
   */
  public static ProcessInstanceEvent startScopedProcessInstance(
      final CamundaClient camundaClient,
      final long processDefinitionKey,
      final String scopeId,
      final Map<String, Object> variables) {

    final Map<String, Object> variablesCopy = new HashMap<>(variables);
    variablesCopy.put(VAR_TEST_SCOPE_ID, scopeId);

    return camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(variablesCopy)
        .execute();
  }

  /**
   * Starts a process instance with a process variable {@link #VAR_TEST_SCOPE_ID} for scope and
   * additional variables.
   *
   * @param camundaClient
   * @param bpmnProcessId
   * @param scopeId ... some unique id for the test case
   * @param variables ... additional variables
   */
  public static ProcessInstanceEvent startScopedProcessInstance(
      final CamundaClient camundaClient,
      final String bpmnProcessId,
      final String scopeId,
      final Map<String, Object> variables) {

    final Map<String, Object> variablesCopy = new HashMap<>(variables);
    variablesCopy.put(VAR_TEST_SCOPE_ID, scopeId);

    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .variables(variablesCopy)
        .execute();
  }

  /**
   * Waits for process instances to start which are having a process variable {@link
   * #VAR_TEST_SCOPE_ID}.
   *
   * @param camundaClient ... CamundaClient
   * @param scopeId ... some unique id for the test case
   */
  public static void waitForScopedProcessInstancesToStart(
      final CamundaClient camundaClient, final String scopeId, final int expectedProcessInstances) {
    waitForItemsPaginated(
        "should wait until process instances are available",
        expectedProcessInstances,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(f -> f.variables(getScopedVariables(scopeId)))
                .page(page)
                .execute());
  }

  /**
   * Waits for active process instances which are having a process variable {@link
   * #VAR_TEST_SCOPE_ID}.
   *
   * @param camundaClient ... CamundaClient
   * @param scopeId ... some unique id for the test case
   */
  public static void waitForScopedActiveProcessInstances(
      final CamundaClient camundaClient, final String scopeId, final int expectedProcessInstances) {
    waitForItemsPaginated(
        "should wait until process instances are available and active",
        expectedProcessInstances,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(
                    f ->
                        f.state(ProcessInstanceState.ACTIVE).variables(getScopedVariables(scopeId)))
                .page(page)
                .execute());
  }

  /**
   * Waits for process instances to start which are having a process variable {@link *
   * #VAR_TEST_SCOPE_ID}.
   *
   * @param camundaClient CamundaClient
   * @param processInstanceKey the key of the process instance to check
   * @param variableName the name of the variable to check
   * @param variableValue the value of the variable to check
   */
  public static void waitUntilProcessInstanceHasVariable(
      final CamundaClient camundaClient,
      final Long processInstanceKey,
      final String variableName,
      final String variableValue) {
    waitForItemsPaginated(
        "should wait until variables exist",
        1,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(
                    f ->
                        f.processInstanceKey(processInstanceKey)
                            .variables(Map.of(variableName, variableValue)))
                .page(page)
                .execute());
  }

  /**
   * Waits for user tasks to be created which are having a process variable {@link
   * #VAR_TEST_SCOPE_ID}.
   *
   * @param client ... CamundaClient
   * @param testScopeId ... some unique id for the test case
   * @param expectedCount ... expected number of user tasks
   */
  public static void waitForActiveScopedUserTasks(
      final CamundaClient client, final String testScopeId, final int expectedCount) {
    waitForItemsPaginated(
        "should have items with state",
        expectedCount,
        page ->
            client
                .newUserTaskSearchRequest()
                .filter(
                    f ->
                        f.state(UserTaskState.CREATED)
                            .processInstanceVariables(getScopedVariables(testScopeId)))
                .page(page)
                .execute());
  }

  /**
   * Returns a map with the process variable {@link #VAR_TEST_SCOPE_ID} and the given scopeId. Where
   * the scopeId already has the additional necessary double quotes for the query. (Needed until
   * this is solved: https://github.com/camunda/camunda/issues/23724)
   *
   * @param scopeId
   * @return a map with the process variable {@link #VAR_TEST_SCOPE_ID} and the given scopeId
   */
  public static Map<String, Object> getScopedVariables(final String scopeId) {
    return Map.of(VAR_TEST_SCOPE_ID, "\"" + scopeId + "\"");
  }

  public static void waitForUserTasks(
      final CamundaClient client, final Consumer<UserTaskFilter> filter, final int expectedCount) {
    waitForItemsPaginated(
        "should have items with state",
        expectedCount,
        page -> client.newUserTaskSearchRequest().filter(filter).page(page).execute());
  }

  public static UserTask waitForUserTask(
      final CamundaClient client, final Consumer<UserTaskFilter> filter) {
    final UserTask[] migratedTask = new UserTask[1];
    Awaitility.await("Should find user task with filter")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              migratedTask[0] =
                  client.newUserTaskSearchRequest().filter(filter).send().join().singleItem();
            });
    return migratedTask[0];
  }

  public static void waitForBatchOperationWithCorrectTotalCount(
      final CamundaClient camundaClient, final String batchOperationKey, final int expectedItems) {
    Awaitility.await("should start batch operation with correct total count")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptionsMatching(
            e ->
                e instanceof ProblemException
                    && ((ProblemException) e).code() == 404) // Ignore 404 errors
        .untilAsserted(
            () -> {
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).execute();
              assertThat(batch).isNotNull();
              assertThat(batch.getOperationsTotalCount()).isEqualTo(expectedItems);
            });
  }

  public static void waitForBatchOperationCompleted(
      final CamundaClient camundaClient,
      final String batchOperationKey,
      final int expectedCompletedItems,
      final int expectedFailedItems) {
    Awaitility.await("should complete batch operation with correct completed/failed count")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).execute();
              assertThat(batch).isNotNull();
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
              assertThat(batch.getOperationsCompletedCount()).isEqualTo(expectedCompletedItems);
              assertThat(batch.getOperationsFailedCount()).isEqualTo(expectedFailedItems);
            });
  }

  public static void waitForBatchOperationStatus(
      final CamundaClient camundaClient,
      final String batchOperationKey,
      final BatchOperationState expectedStatus) {
    Awaitility.await("batch operation should have state " + expectedStatus)
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).execute();
              assertThat(batch).isNotNull();
              assertThat(batch.getStatus()).isEqualTo(expectedStatus);
            });
  }

  public static void waitForBatchOperationStatus(
      final CamundaClient camundaClient,
      final String batchOperationKey,
      final Set<BatchOperationState> acceptedStates) {
    Awaitility.await("batch operation should have state " + acceptedStates)
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).execute();
              assertThat(batch).isNotNull();
              assertThat(batch.getStatus())
                  .withFailMessage(
                      "Expected batch operation to have one of the states %s, but was %s",
                      acceptedStates, batch.getStatus())
                  .isIn(acceptedStates);
            });
  }

  public static void waitForProcessInstanceToBeTerminated(
      final CamundaClient camundaClient, final Long processInstanceKey) {
    Awaitility.await("should wait until process is terminated")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newProcessInstanceGetRequest(processInstanceKey).execute();
              assertThat(result.getState()).isEqualTo(ProcessInstanceState.TERMINATED);
            });
  }

  public static void waitForProcessInstancesToBeCompleted(
      final CamundaClient camundaClient,
      final Consumer<ProcessInstanceFilter> fn,
      final int expectedCount) {
    waitForItemsPaginated(
        "should wait for process instances to be completed",
        expectedCount,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .page(page)
                .filter(fn.andThen(f -> f.state(ProcessInstanceState.COMPLETED)))
                .execute());
  }

  public static void waitForProcessInstance(
      final CamundaClient client,
      final Consumer<ProcessInstanceFilter> filter,
      final Consumer<List<ProcessInstance>> asserter) {
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newProcessInstanceSearchRequest().filter(filter).execute().items();
              asserter.accept(result);
            });
  }

  public static void waitForElementInstances(
      final CamundaClient camundaClient, final int expectedElementInstances) {
    waitForElementInstances(camundaClient, f -> {}, expectedElementInstances);
  }

  public static void waitForElementInstances(
      final CamundaClient camundaClient,
      final Consumer<ElementInstanceFilter> filter,
      final int expectedElementInstances) {
    waitForItemsPaginated(
        "should wait until element instances are available",
        expectedElementInstances,
        page ->
            camundaClient.newElementInstanceSearchRequest().filter(filter).page(page).execute());
  }

  public static List<Job> waitForJobs(
      final CamundaClient camundaClient, final List<Long> processInstanceKeys) {
    return waitForItemsPaginated(
        "should wait until jobs are available",
        processInstanceKeys.size(),
        page ->
            camundaClient
                .newJobSearchRequest()
                .filter(f -> f.processInstanceKey(b -> b.in(processInstanceKeys)))
                .page(page)
                .execute());
  }

  /**
   * Waits for jobs matching the given filter to reach the expected count.
   *
   * @param camundaClient the Camunda client
   * @param filter the filter to apply to the job search
   * @param expectedCount the expected number of jobs
   */
  public static void waitForJobs(
      final CamundaClient camundaClient,
      final Consumer<JobFilter> filter,
      final int expectedCount) {
    Awaitility.await("should wait until jobs are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newJobSearchRequest().filter(filter).send().join().items();
              assertThat(result).hasSize(expectedCount);
            });
  }

  /**
   * Activates and completes jobs of the given type for a specific tenant.
   *
   * @param camundaClient the Camunda client
   * @param jobType the job type to activate
   * @param tenantId the tenant ID (can be null for default tenant)
   * @param workerName the worker name to use
   * @param count the number of jobs to complete
   * @return the list of completed job keys
   */
  public static List<Long> activateAndCompleteJobsForTenant(
      final CamundaClient camundaClient,
      final String jobType,
      final String tenantId,
      final String workerName,
      final int count) {
    var command =
        camundaClient
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(count)
            .workerName(workerName)
            .timeout(Duration.ofMinutes(5));

    if (tenantId != null) {
      command = command.tenantIds(tenantId);
    }

    final var jobs = command.send().join().getJobs();

    for (final var job : jobs) {
      camundaClient.newCompleteCommand(job.getKey()).send().join();
    }

    return jobs.stream().map(ActivatedJob::getKey).collect(Collectors.toList());
  }

  /**
   * Activates and completes jobs of the given type.
   *
   * @param camundaClient the Camunda client
   * @param jobType the job type to activate
   * @param workerName the worker name to use
   * @param count the number of jobs to complete
   * @return the list of completed job keys
   */
  public static List<Long> activateAndCompleteJobs(
      final CamundaClient camundaClient,
      final String jobType,
      final String workerName,
      final int count) {
    return activateAndCompleteJobsForTenant(camundaClient, jobType, null, workerName, count);
  }

  /**
   * Activates and fails jobs of the given type for a specific tenant.
   *
   * @param camundaClient the Camunda client
   * @param jobType the job type to activate
   * @param tenantId the tenant ID (can be null for default tenant)
   * @param workerName the worker name to use
   * @param count the number of jobs to fail
   * @param errorMessage the error message for the failure
   */
  public static List<Long> activateAndFailJobsForTenant(
      final CamundaClient camundaClient,
      final String jobType,
      final String tenantId,
      final String workerName,
      final int count,
      final String errorMessage) {
    var command =
        camundaClient
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(count)
            .workerName(workerName)
            .timeout(Duration.ofMinutes(5));

    if (tenantId != null) {
      command = command.tenantIds(tenantId);
    }

    final var jobs = command.send().join().getJobs();

    for (final var job : jobs) {
      camundaClient
          .newFailCommand(job.getKey())
          .retries(0)
          .errorMessage(errorMessage)
          .send()
          .join();
    }

    return jobs.stream().map(ActivatedJob::getKey).collect(Collectors.toList());
  }

  /**
   * Activates and fails jobs of the given type.
   *
   * @param camundaClient the Camunda client
   * @param jobType the job type to activate
   * @param workerName the worker name to use
   * @param count the number of jobs to fail
   * @param errorMessage the error message for the failure
   * @return the list of failed job keys
   */
  public static List<Long> activateAndFailJobs(
      final CamundaClient camundaClient,
      final String jobType,
      final String workerName,
      final int count,
      final String errorMessage) {
    return activateAndFailJobsForTenant(
        camundaClient, jobType, null, workerName, count, errorMessage);
  }

  /**
   * Waits for job statistics to be exported and match the given requirements.
   *
   * @param camundaClient the Camunda client
   * @param startTime the start time for the statistics query
   * @param endTime the end time for the statistics query
   * @param fnRequirements the assertions to apply to the statistics
   */
  public static void waitForJobStatistics(
      final CamundaClient camundaClient,
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final Consumer<GlobalJobStatistics> fnRequirements) {
    waitForJobStatistics(camundaClient, startTime, endTime, null, fnRequirements);
  }

  /**
   * Waits for job statistics to be exported and match the given requirements, filtered by job type.
   *
   * @param camundaClient the Camunda client
   * @param startTime the start time for the statistics query
   * @param endTime the end time for the statistics query
   * @param jobType the job type to filter by (can be null for no filter)
   * @param fnRequirements the assertions to apply to the statistics
   */
  public static void waitForJobStatistics(
      final CamundaClient camundaClient,
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final String jobType,
      final Consumer<GlobalJobStatistics> fnRequirements) {
    Awaitility.await("should export job metrics to secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var request = camundaClient.newGlobalJobStatisticsRequest(startTime, endTime);

              if (jobType != null) {
                request = request.jobType(jobType);
              }

              assertThat(request.send().join()).satisfies(fnRequirements);
            });
  }

  public static void waitForProcessInstances(
      final CamundaClient camundaClient,
      final Consumer<ProcessInstanceFilter> fn,
      final int expectedProcessInstances) {
    waitForItemsPaginated(
        "should wait until process instances are available",
        expectedProcessInstances,
        page -> camundaClient.newProcessInstanceSearchRequest().filter(fn).page(page).execute());
  }

  public static void waitForTenantDeletion(
      final CamundaClient camundaClient, final String tenantToBeDeleted) {
    Awaitility.await("should wait until tenant is deleted")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient.newTenantsSearchRequest().execute().items().stream()
                            .map(Tenant::getTenantId)
                            .collect(Collectors.toSet()))
                    .doesNotContain(tenantToBeDeleted));
  }

  public static void waitForProcesses(
      final CamundaClient camundaClient,
      final Consumer<ProcessDefinitionFilter> fn,
      final int expectedProcessDefinitions) {
    waitForItemsPaginated(
        "should wait until processes are available",
        expectedProcessDefinitions,
        page -> camundaClient.newProcessDefinitionSearchRequest().filter(fn).page(page).execute());
  }

  public static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedProcessDefinitions) {
    waitForItemsPaginated(
        "should deploy processes and import in Operate",
        expectedProcessDefinitions,
        page -> camundaClient.newProcessDefinitionSearchRequest().page(page).execute());
  }

  public static void waitForStartFormsBeingExported(
      final CamundaClient camundaClient,
      final String processDefinitionId,
      final String expectedFormId,
      final long expectedFormVersion) {
    Awaitility.await("should export start forms to secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var processDefinitionKey =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .execute()
                      .items()
                      .getFirst()
                      .getProcessDefinitionKey();
              final var form =
                  camundaClient.newProcessDefinitionGetFormRequest(processDefinitionKey).execute();
              assertThat(form.getFormId()).isEqualTo(expectedFormId);
              assertThat(form.getVersion()).isEqualTo(expectedFormVersion);
            });
  }

  public static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient,
      final Consumer<ProcessDefinitionFilter> filter,
      final int expectedProcessDefinitions) {
    waitForItemsPaginated(
        "should deploy processes and import in secondary database",
        expectedProcessDefinitions,
        page ->
            camundaClient.newProcessDefinitionSearchRequest().filter(filter).page(page).execute());
  }

  public static Decision deployDefaultTestDecisionProcessInstance(
      final CamundaClient camundaClient, final String dmnResource) {
    final var decisionDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(String.format("decisions/%s", dmnResource))
            .execute()
            .getDecisions()
            .getFirst();
    waitForDecisionsToBeDeployed(
        camundaClient,
        decisionDeployment.getDecisionKey(),
        decisionDeployment.getDecisionRequirementsKey(),
        1,
        1);
    return decisionDeployment;
  }

  public static Decision deployDmnModel(
      final CamundaClient camundaClient,
      final DmnModelInstance dmnModel,
      final String resourceName) {
    final DeploymentEvent deploymentEvent =
        camundaClient
            .newDeployResourceCommand()
            .addResourceStream(
                new ByteArrayInputStream(Dmn.convertToString(dmnModel).getBytes()),
                resourceName + ".dmn")
            .send()
            .join();
    return deploymentEvent.getDecisions().getFirst();
  }

  public static EvaluateDecisionResponse evaluateDecision(
      final CamundaClient camundaClient, final long decisionKey, final String variables) {
    return camundaClient
        .newEvaluateDecisionCommand()
        .decisionKey(decisionKey)
        .variables(variables)
        .send()
        .join();
  }

  public static void waitForDecisionInstanceCount(
      final CamundaClient camundaClient,
      final Consumer<DecisionInstanceFilter> filter,
      final int expectedResultCount) {
    Awaitility.await("Expected amount of decision instances in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newDecisionInstanceSearchRequest()
                          .filter(filter)
                          .send()
                          .join()
                          .items())
                  .hasSize(expectedResultCount);
            });
  }

  public static Process startDefaultTestDecisionProcessInstance(
      final CamundaClient camundaClient, final String dmnDecisionId, final String bpmnProcessId) {
    final var deployment =
        deployResource(
                camundaClient,
                Bpmn.createExecutableProcess(bpmnProcessId)
                    .startEvent()
                    .businessRuleTask("dmn_task")
                    .zeebeCalledDecisionId(dmnDecisionId)
                    .zeebeResultVariable("{\"output1\": \"B\"}")
                    .endEvent()
                    .done(),
                "dmn_process.bpmn")
            .getProcesses()
            .getFirst();
    final long processInstanceKey =
        startProcessInstance(camundaClient, bpmnProcessId).getProcessInstanceKey();
    waitForDecisionToBeEvaluated(camundaClient, processInstanceKey, 1);
    return deployment;
  }

  public static DeploymentEvent deployServiceTaskProcess(
      final CamundaClient camundaClient,
      final String bpmnProcessId,
      final String name,
      final String retries) {
    return deployServiceTaskProcess(camundaClient, bpmnProcessId, name, retries, "<default>");
  }

  public static DeploymentEvent deployServiceTaskProcess(
      final CamundaClient camundaClient,
      final String bpmnProcessId,
      final String name,
      final String retries,
      final String tenantId) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .name(name)
            .startEvent("start")
            .serviceTask("serviceTask", t -> t.zeebeJobType("type").zeebeJobRetries(retries))
            .endEvent("end")
            .done();

    return deployResourceForTenant(camundaClient, model, "service_task_process.bpmn", tenantId);
  }

  public static void waitForDecisionsToBeDeployed(
      final CamundaClient camundaClient,
      final long decisionKey,
      final long decisionRequirementsKey,
      final int expectedDecisionDefinitions,
      final int expectedDecisionRequirements) {
    final DecisionDefinitionFilter decisionDefinitionFilter =
        decisionKey != -1
            ? new DecisionDefinitionFilterImpl().decisionDefinitionKey(decisionKey)
            : new DecisionDefinitionFilterImpl();
    final DecisionRequirementsFilter decisionRequirementsFilter =
        decisionRequirementsKey != -1
            ? new DecisionRequirementsFilterImpl().decisionRequirementsKey(decisionRequirementsKey)
            : new DecisionRequirementsFilterImpl();
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newDecisionDefinitionSearchRequest()
                          .filter(decisionDefinitionFilter)
                          .execute()
                          .items()
                          .size())
                  .isEqualTo(expectedDecisionDefinitions);
              assertThat(
                      camundaClient
                          .newDecisionRequirementsSearchRequest()
                          .filter(decisionRequirementsFilter)
                          .execute()
                          .items()
                          .size())
                  .isEqualTo(expectedDecisionRequirements);
            });
  }

  public static void waitForDecisionsToBeDeployed(
      final CamundaClient camundaClient,
      final int expectedDecisionDefinitions,
      final int expectedDecisionRequirements) {
    waitForDecisionsToBeDeployed(
        camundaClient, -1, -1, expectedDecisionDefinitions, expectedDecisionRequirements);
  }

  public static void waitForDecisionToBeEvaluated(
      final CamundaClient camundaClient,
      final long processInstanceKey,
      final int expectedDecisionInstances) {
    Awaitility.await("should deploy decision definitions and wait for import")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newDecisionInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .execute();
              assertThat(result.items()).hasSize(expectedDecisionInstances);
            });
  }

  public static void waitUntilProcessInstanceHasIncidents(
      final CamundaClient camundaClient, final int expectedInstancesWithIncidents) {
    waitForItemsPaginated(
        "should wait until incidents are exists",
        expectedInstancesWithIncidents,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(f -> f.hasIncident(true))
                .page(page)
                .execute());
  }

  public static void waitUntilIncidentIsResolvedOnProcessInstance(
      final CamundaClient camundaClient, final int expectedProcessInstances) {
    waitForItemsPaginated(
        "should wait until incidents are resolved",
        expectedProcessInstances,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(f -> f.hasIncident(false))
                .page(page)
                .execute());
  }

  public static void waitUntilIncidentIsResolvedOnElementInstance(
      final CamundaClient camundaClient, final int expectedElementInstances) {
    waitForItemsPaginated(
        "should wait until incidents are resolved",
        expectedElementInstances,
        page ->
            camundaClient
                .newElementInstanceSearchRequest()
                .filter(f -> f.hasIncident(false))
                .page(page)
                .execute());
  }

  public static void waitUntilIncidentsAreActive(
      final CamundaClient camundaClient, final int expectedIncidents) {
    waitUntilIncidentsConditionsAreMet(
        camundaClient,
        f -> f.state(IncidentState.ACTIVE),
        expectedIncidents,
        "should wait until incidents become active");
  }

  public static void waitUntilIncidentsConditionsAreMet(
      final CamundaClient camundaClient,
      final Consumer<IncidentFilter> filter,
      final int expectedIncidents,
      final String awaitMsg) {
    waitForItemsPaginated(
        awaitMsg,
        expectedIncidents,
        page -> camundaClient.newIncidentSearchRequest().filter(filter).page(page).execute());
  }

  public static void waitUntilIncidentsAreResolved(
      final CamundaClient camundaClient, final int expectedIncidents) {
    waitForItemsPaginated(
        "should wait until incidents are resolved",
        expectedIncidents,
        page ->
            camundaClient
                .newIncidentSearchRequest()
                .filter(f -> f.state(IncidentState.RESOLVED))
                .page(page)
                .execute());
  }

  public static void waitUntilIncidentsAreResolved(
      final CamundaClient camundaClient, final List<Long> incidentKeys) {
    waitForItemsPaginated(
        "should wait until incidents are resolved",
        incidentKeys.size(),
        page ->
            camundaClient
                .newIncidentSearchRequest()
                .filter(f -> f.state(IncidentState.RESOLVED).incidentKey(k -> k.in(incidentKeys)))
                .page(page)
                .execute());
  }

  public static void waitUntilProcessInstanceIsEnded(
      final CamundaClient camundaClient, final long processInstanceKey) {
    Awaitility.await("should wait until process is ended")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .execute();
              assertThat(result.items().getFirst().getEndDate()).isNotNull();
            });
  }

  public static void waitUntilElementInstanceHasIncidents(
      final CamundaClient camundaClient, final int expectedIncidents) {
    waitForItemsPaginated(
        "should wait until element instance has incidents",
        expectedIncidents,
        page ->
            camundaClient
                .newElementInstanceSearchRequest()
                .filter(f -> f.hasIncident(true))
                .page(page)
                .execute());
  }

  public static void waitUntilFailedJobIncident(
      final CamundaClient camundaClient, final int expectedIncidents) {
    waitForItemsPaginated(
        "should wait until failed job incidents are available",
        expectedIncidents,
        page ->
            camundaClient
                .newIncidentSearchRequest()
                .filter(f -> f.jobKey(b -> b.exists(true)))
                .page(page)
                .execute());
  }

  public static void waitUntilJobWorkerHasFailedJob(
      final CamundaClient camundaClient,
      final Map<String, Object> variables,
      final int expectedProcesses) {
    waitForItemsPaginated(
        "should wait until the process instance has been updated to reflect retries left.",
        expectedProcesses,
        page ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(f -> f.hasRetriesLeft(true).variables(variables))
                .page(page)
                .execute());
  }

  public static void waitUntilJobExistsForProcessInstance(
      final CamundaClient camundaClient, final long processInstanceKey) {
    waitForItemsPaginated(
        "should wait until the process instance has an active job.",
        1,
        page ->
            camundaClient
                .newJobSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .page(page)
                .execute());
  }

  public static void waitUntilExactUsersExist(
      final CamundaClient camundaClient, final String... usernames) {
    await("should wait until the expected users are in the secondary storage.")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUsersSearchRequest().execute();
              final var users = result.items();
              assertThat(users)
                  .extracting(u -> u.getUsername())
                  .containsExactlyInAnyOrder(usernames);
            });
  }

  public static void waitForMessageSubscriptions(
      final CamundaClient camundaClient, final int expectedMessageSubscriptions) {
    waitForMessageSubscriptions(camundaClient, f -> {}, expectedMessageSubscriptions);
  }

  public static void waitForMessageSubscriptions(
      final CamundaClient camundaClient,
      final Consumer<MessageSubscriptionFilter> filterConsumer,
      final int expectedMessageSubscriptions) {
    waitForItemsPaginated(
        "should wait until message subscriptions are available",
        expectedMessageSubscriptions,
        page ->
            camundaClient
                .newMessageSubscriptionSearchRequest()
                .filter(filterConsumer)
                .page(page)
                .execute());
  }

  public static void waitForCorrelatedMessageSubscriptions(
      final CamundaClient camundaClient, final int expectedCorrelatedMessageSubscriptions) {
    waitForItemsPaginated(
        "should wait until correlated message subscriptions are available",
        expectedCorrelatedMessageSubscriptions,
        page -> camundaClient.newCorrelatedMessageSubscriptionSearchRequest().page(page).execute());
  }

  public static void waitUntilAuthorizationVisible(
      final CamundaClient camundaClient, final String owner, final String resource) {
    Awaitility.await("should wait until authorization is visible")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .filter(f -> f.ownerId(owner).resourceIds(resource))
                      .execute();
              assertThat(result.items().size()).isOne();
            });
  }

  public static void waitForUserGroupAssignment(
      final CamundaClient camundaClient, final String groupId, final String username) {
    Awaitility.await("should wait until user group assignment is visible")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(camundaClient.newUsersByGroupSearchRequest(groupId).execute().items())
                    .extracting(GroupUser::getUsername)
                    .contains(username));
  }

  public static void waitForUserRoleAssignment(
      final CamundaClient camundaClient, final String roleId, final String username) {
    Awaitility.await("should wait until user role assignment is visible")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(camundaClient.newUsersByRoleSearchRequest(roleId).execute().items())
                    .extracting(RoleUser::getUsername)
                    .contains(username));
  }

  public static <T, U extends Comparable<U>> void assertSorted(
      final SearchResponse<T> resultAsc,
      final SearchResponse<T> resultDesc,
      final Function<T, U> propertyExtractor) {
    assertThatIsAscSorted(resultAsc.items(), propertyExtractor);
    assertThatIsDescSorted(resultDesc.items(), propertyExtractor);
  }

  /**
   * Asserts that the given search responses are sorted either in a case-insensitive way or in a
   * Java natural order way. This flexibility is needed because different databases might sort
   * strings differently. To not make the assertion depend on a specific database behavior, we allow
   * both sorting methods.
   */
  public static <T> void assertSortedFlexible(
      final SearchResponse<T> resultAsc,
      final SearchResponse<T> resultDesc,
      final Function<T, String> propertyExtractor) {

    // The AWS Aurora instances we test against use a collation based on Thai locale.
    final var locale = Locale.of("th");
    final Collator collator = Collator.getInstance(locale);
    collator.setStrength(Collator.SECONDARY); // case-insensitive, accent-sensitive
    final Comparator<String> stringComparator = collator::compare;

    assertThat(resultAsc)
        .satisfiesAnyOf(
            result ->
                assertThatIsSortedBy(result.items(), propertyExtractor, CASE_INSENSITIVE_ORDER),
            result -> assertThatIsAscSorted(result.items(), propertyExtractor),
            result -> assertThatIsSortedBy(result.items(), propertyExtractor, stringComparator));
    assertThat(resultDesc)
        .satisfiesAnyOf(
            result ->
                assertThatIsSortedBy(
                    result.items(), propertyExtractor, CASE_INSENSITIVE_ORDER.reversed()),
            result -> assertThatIsDescSorted(result.items(), propertyExtractor),
            result ->
                assertThatIsSortedBy(
                    result.items(), propertyExtractor, stringComparator.reversed()));
  }

  public static <T, U extends Comparable<U>> void assertThatIsAscSorted(
      final List<T> items, final Function<T, U> propertyExtractor) {
    assertThatIsSortedBy(items, propertyExtractor, Comparator.naturalOrder());
  }

  public static <T, U extends Comparable<U>> void assertThatIsDescSorted(
      final List<T> items, final Function<T, U> propertyExtractor) {
    assertThatIsSortedBy(items, propertyExtractor, Comparator.reverseOrder());
  }

  public static <T, U extends Comparable<U>> void assertThatIsSortedBy(
      final List<T> items, final Function<T, U> propertyExtractor, final Comparator<U> comparator) {
    final var sorted =
        items.stream().map(propertyExtractor).filter(Objects::nonNull).sorted(comparator).toList();
    assertThat(items.stream().map(propertyExtractor).filter(Objects::nonNull).toList())
        .containsExactlyElementsOf(sorted);
  }

  /**
   * Waits for a cluster variable to be indexed in Elasticsearch/OpenSearch after creation.
   *
   * @param camundaClient CamundaClient
   * @param variableName the name of the cluster variable to retrieve
   * @param expectedValue the expected value of the variable
   */
  public static void waitForClusterVariableToBeIndexed(
      final CamundaClient camundaClient, final String variableName, final String expectedValue) {
    Awaitility.await("should index cluster variable")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newGloballyScopedClusterVariableGetRequest()
                      .withName(variableName)
                      .execute();
              assertThat(result).isNotNull();
              assertThat(result.getValue()).contains(expectedValue);
            });
  }

  /**
   * Waits for a tenant-scoped cluster variable to be indexed in Elasticsearch/OpenSearch after
   * creation.
   *
   * @param camundaClient CamundaClient
   * @param variableName the name of the cluster variable to retrieve
   * @param tenantId the tenant ID of the variable
   * @param expectedValue the expected value of the variable
   */
  public static void waitForClusterVariableToBeIndexed(
      final CamundaClient camundaClient,
      final String variableName,
      final String tenantId,
      final String expectedValue) {
    Awaitility.await("should index tenant-scoped cluster variable")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newTenantScopedClusterVariableGetRequest(tenantId)
                      .withName(variableName)
                      .execute();
              assertThat(result).isNotNull();
              assertThat(result.getValue()).contains(expectedValue);
            });
  }

  /**
   * Waits for multiple cluster variables to be indexed using search requests. This method verifies
   * that all variables in the provided map have been exported and indexed.
   *
   * @param camundaClient CamundaClient
   * @param variablesToWaitFor a map of variable names to their expected values
   */
  public static void waitForClusterVariablesToBeIndexed(
      final CamundaClient camundaClient, final Map<String, String> variablesToWaitFor) {
    Awaitility.await("should index all cluster variables")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var response = camundaClient.newClusterVariableSearchRequest().execute();
              assertThat(response).isNotNull();
              assertThat(response.items()).isNotEmpty();

              // Verify each expected variable is present with correct value
              for (final var entry : variablesToWaitFor.entrySet()) {
                final var expectedName = entry.getKey();
                final var expectedValue = entry.getValue();

                assertThat(response.items())
                    .anySatisfy(item -> assertThat(item.getName()).isEqualTo(expectedName));
              }
            });
  }

  /**
   * Waits for all provided CamundaFutures to complete. This method executes all futures in parallel
   * and blocks until all have completed.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * var futures = IntStream.rangeClosed(1, 10)
   *   .mapToObj(i -> camundaClient.newCreateInstanceCommand()...send())
   *   .toList();
   * waitForAll(futures);
   * }</pre>
   *
   * @param futures list of CamundaFutures to wait for
   * @throws io.camunda.client.api.command.ClientException on unexpected errors
   * @throws io.camunda.client.api.command.ClientStatusException on gRPC errors
   */
  public static void waitForAll(final List<? extends CamundaFuture<?>> futures) {
    if (futures == null || futures.isEmpty()) {
      return;
    }
    CompletableFuture.allOf(
            futures.stream()
                .map(CamundaFuture::toCompletableFuture)
                .toArray(CompletableFuture[]::new))
        .join();
  }

  /**
   * Asserts that all data related to a process instance has been deleted from secondary storage.
   */
  public static void assertAllProcessInstanceDependantDataDeleted(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("All process instance data should be deleted")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final Map<String, Collection<?>> results =
                  getDataVerifiers(client, processInstanceKey).entrySet().parallelStream()
                      .collect(
                          Collectors.toConcurrentMap(Map.Entry::getKey, e -> e.getValue().get()));

              assertThat(results)
                  .as("All verifiers should find no data")
                  .allSatisfy((name, data) -> assertThat(data).as("Data for %s", name).isEmpty());
            });
  }

  /**
   * Returns a map of data verifiers for exhaustive checking of all process instance related data in
   * secondary storage.
   */
  private static Map<String, Supplier<Collection<?>>> getDataVerifiers(
      final CamundaClient client, final long processInstanceKey) {
    return Map.of(
        "process instance",
        () ->
            client
                .newProcessInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "element instance",
        () ->
            client
                .newElementInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "variable",
        () ->
            client
                .newVariableSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "incident",
        () ->
            client
                .newIncidentSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "job",
        () ->
            client
                .newJobSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "user task",
        () ->
            client
                .newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "decision instance",
        () ->
            client
                .newDecisionInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "correlated message subscription",
        () ->
            client
                .newCorrelatedMessageSubscriptionSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "sequence flow",
        () -> client.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join());
  }
}
