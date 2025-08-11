/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.util;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.impl.search.filter.DecisionDefinitionFilterImpl;
import io.camunda.client.impl.search.filter.DecisionRequirementsFilterImpl;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.awaitility.Awaitility;

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

  public static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
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
            .send()
            .join()
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
        .send()
        .join();
  }

  public static DeploymentEvent deployResourceForTenant(
      final CamundaClient camundaClient, final String resourceName, final String tenantId) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenantId)
        .send()
        .join();
  }

  public static void createTenant(
      final CamundaClient client,
      final String tenantId,
      final String tenantName,
      final String... usernames) {
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantName).send().join();
    for (final var username : usernames) {
      client.newAssignUserToTenantCommand().username(username).tenantId(tenantId).send().join();
    }
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .send()
        .join();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.send().join();
  }

  public static void waitForProcessInstancesToStart(
      final CamundaClient camundaClient, final int expectedProcessInstances) {
    waitForProcessInstancesToStart(camundaClient, f -> {}, expectedProcessInstances);
  }

  public static void waitForProcessInstancesToStart(
      final CamundaClient camundaClient,
      final Consumer<ProcessInstanceFilter> filter,
      final int expectedProcessInstances) {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newProcessInstanceSearchRequest().filter(filter).send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessInstances);
            });
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
        .send()
        .join();
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
        .send()
        .join();
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
    Awaitility.await("should wait until process instances are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.variables(getScopedVariables(scopeId)))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessInstances);
            });
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
    Awaitility.await("should wait until process instances are available and active")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(
                          f ->
                              f.state(ProcessInstanceState.ACTIVE)
                                  .variables(getScopedVariables(scopeId)))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessInstances);
            });
  }

  /**
   * Waits for process instances to start which are having a process variable {@link *
   * #VAR_TEST_SCOPE_ID}.
   *
   * @param camundaClient ... CamundaClient
   * @param expectedIncidents
   */
  public static void waitUntilScopedProcessInstanceHasIncidents(
      final CamundaClient camundaClient, final String scopeId, final int expectedIncidents) {
    Awaitility.await("should wait until scoped incidents are exists")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.variables(getScopedVariables(scopeId)).hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
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
    final Map<String, Object> variables = new HashMap<>();
    variables.put(variableName, variableValue);

    Awaitility.await("should wait until variables exist")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(
                          f ->
                              f.processInstanceKey(processInstanceKey)
                                  .variables(Map.of(variableName, variableValue)))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(1);
            });
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
    await("should have items with state")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var userTasks =
                  client
                      .newUserTaskSearchRequest()
                      .filter(
                          f ->
                              f.state(UserTaskState.CREATED)
                                  .processInstanceVariables(getScopedVariables(testScopeId)))
                      .send()
                      .join()
                      .items();
              assertThat(userTasks).hasSize(expectedCount);
            });
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
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
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
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
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
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
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
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
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
                  camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();
              assertThat(result.getState()).isEqualTo(ProcessInstanceState.TERMINATED);
            });
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
                  client.newProcessInstanceSearchRequest().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public static void waitForElementInstances(
      final CamundaClient camundaClient, final int expectedElementInstances) {
    Awaitility.await("should wait until element instances are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newElementInstanceSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedElementInstances);
            });
  }

  public static void waitForElementInstances(
      final CamundaClient camundaClient,
      final Consumer<ElementInstanceFilter> filter,
      final int expectedElementInstances) {
    Awaitility.await("should wait until element instances are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newElementInstanceSearchRequest().filter(filter).send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedElementInstances);
            });
  }

  public static void waitForProcessInstances(
      final CamundaClient camundaClient,
      final Consumer<ProcessInstanceFilter> fn,
      final int expectedProcessInstances) {
    Awaitility.await("should wait until process instances are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(fn)
                            .send()
                            .join()
                            .items())
                    .hasSize(expectedProcessInstances));
  }

  public static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedProcessDefinitions) {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessDefinitions);
            });
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
                      .send()
                      .join()
                      .items()
                      .getFirst()
                      .getProcessDefinitionKey();
              final var form =
                  camundaClient
                      .newProcessDefinitionGetFormRequest(processDefinitionKey)
                      .send()
                      .join();
              assertThat(form.getFormId()).isEqualTo(expectedFormId);
              assertThat(form.getVersion()).isEqualTo(expectedFormVersion);
            });
  }

  public static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient,
      final Consumer<ProcessDefinitionFilter> filter,
      final int expectedProcessDefinitions) {
    Awaitility.await("should deploy processes and import in secondary database")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newProcessDefinitionSearchRequest().filter(filter).send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessDefinitions);
            });
  }

  public static Decision startDefaultTestDecisionProcessInstance(
      final CamundaClient camundaClient, final String dmnResource) {
    final var decisionDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(String.format("decisions/%s", dmnResource))
            .send()
            .join()
            .getDecisions()
            .getFirst();
    waitForDecisionsToBeDeployed(
        camundaClient,
        decisionDeployment.getDecisionKey(),
        decisionDeployment.getDecisionRequirementsKey(),
        1,
        1);
    deployResource(
            camundaClient,
            Bpmn.createExecutableProcess("dmn_process")
                .startEvent()
                .businessRuleTask("dmn_task")
                .zeebeCalledDecisionId(decisionDeployment.getDmnDecisionId())
                .zeebeResultVariable("{\"output1\": \"B\"}")
                .endEvent()
                .done(),
            "dmn_process.bpmn")
        .getProcesses()
        .getFirst();
    final long processInstanceKey =
        startProcessInstance(camundaClient, "dmn_process").getProcessInstanceKey();
    waitForDecisionToBeEvaluated(camundaClient, processInstanceKey, 1);
    return decisionDeployment;
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
                          .send()
                          .join()
                          .items()
                          .size())
                  .isEqualTo(expectedDecisionDefinitions);
              assertThat(
                      camundaClient
                          .newDecisionRequirementsSearchRequest()
                          .filter(decisionRequirementsFilter)
                          .send()
                          .join()
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
                      .send()
                      .join();
              assertThat(result.items()).hasSize(expectedDecisionInstances);
            });
  }

  public static void waitUntilProcessInstanceHasIncidents(
      final CamundaClient camundaClient, final int expectedIncidents) {
    Awaitility.await("should wait until incidents are exists")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static void waitUntilIncidentIsResolvedOnProcessInstance(
      final CamundaClient camundaClient, final int expectedProcessInstances) {
    Awaitility.await("should wait until incidents are resolved")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.hasIncident(false))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessInstances);
            });
  }

  public static void waitUntilIncidentIsResolvedOnElementInstance(
      final CamundaClient camundaClient, final int expectedElementInstances) {
    Awaitility.await("should wait until incidents are resolved")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newElementInstanceSearchRequest()
                      .filter(f -> f.hasIncident(false))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedElementInstances);
            });
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
    Awaitility.await(awaitMsg)
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newIncidentSearchRequest().filter(filter).send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static void waitUntilIncidentsAreResolved(
      final CamundaClient camundaClient, final int expectedIncidents) {
    Awaitility.await("should wait until incidents are resolved")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newIncidentSearchRequest()
                      .filter(f -> f.state(IncidentState.RESOLVED))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
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
                      .send()
                      .join();
              assertThat(result.items().getFirst().getEndDate()).isNotNull();
            });
  }

  public static void waitUntilElementInstanceHasIncidents(
      final CamundaClient camundaClient, final int expectedIncidents) {
    Awaitility.await("should wait until element instance has incidents")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newElementInstanceSearchRequest()
                      .filter(f -> f.hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static void waitUntilJobWorkerHasFailedJob(
      final CamundaClient camundaClient, final int expectedProcesses) {
    await("should wait until the process instance has been updated to reflect retries left.")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.hasRetriesLeft(true))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(expectedProcesses);
            });
  }

  public static void waitUntilJobExistsForProcessInstance(
      final CamundaClient camundaClient, final long processInstanceKey) {
    await("should wait until the process instance has an active job.")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newJobSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }

  public static void waitUntilExactUsersExist(
      final CamundaClient camundaClient, final String... usernames) {
    await("should wait until the expected users are in the secondary storage.")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUsersSearchRequest().send().join();
              final var users = result.items();
              assertThat(users)
                  .extracting(u -> u.getUsername())
                  .containsExactlyInAnyOrder(usernames);
            });
  }

  public static void waitForMessageSubscriptions(
      final CamundaClient camundaClient, final int expectedMessageSubscriptions) {
    Awaitility.await("should wait until message subscriptions are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result = camundaClient.newMessageSubscriptionSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedMessageSubscriptions);
            });
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
                      .send()
                      .join();
              assertThat(result.items().size()).isOne();
            });
  }

  public static <T, U extends Comparable<U>> void assertSorted(
      final SearchResponse<T> resultAsc,
      final SearchResponse<T> resultDesc,
      final Function<T, U> propertyExtractor) {
    assertThatIsAscSorted(resultAsc.items(), propertyExtractor);
    assertThatIsDescSorted(resultDesc.items(), propertyExtractor);
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
}
