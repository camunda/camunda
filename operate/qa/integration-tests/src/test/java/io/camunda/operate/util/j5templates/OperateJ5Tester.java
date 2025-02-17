/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.client.CamundaClient;
import io.camunda.operate.util.SearchTestRuleProvider;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This is a version of the OperateTester utility specifically for the JUnit 5 integration test
 * templates. This class is intended to be a work in progress and have additional functionality
 * ported over from OperateTester and other legacy test utilities as needed while refactoring
 * integration tests to the new templates
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateJ5Tester {

  @Autowired protected SearchTestRuleProvider searchTestRuleProvider;
  @Autowired protected OperationExecutor operationExecutor;
  private final CamundaClient camundaClient;
  @Autowired private SearchCheckPredicatesHolder searchPredicates;

  @Autowired private MockMvcManager mockMvcManager;

  public OperateJ5Tester(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public Long deployProcess(final String classpathResource) {
    return ZeebeTestUtil.deployProcess(camundaClient, null, classpathResource);
  }

  public void waitForProcessDeployed(final Long processDefinitionKey) {
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessIsDeployedCheck(), processDefinitionKey);
  }

  public Long startProcess(final String bpmnProcessId, final String payload) {
    return ZeebeTestUtil.startProcessInstance(camundaClient, bpmnProcessId, payload);
  }

  public void waitForProcessInstanceStarted(final Long processInstanceKey) {
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstancesAreStartedCheck(), Arrays.asList(processInstanceKey));
  }

  public void waitForProcessInstanceExists(final Long processInstanceKey) {
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstanceExistsCheck(), Arrays.asList(processInstanceKey));
  }

  public void batchProcessInstanceOperation(final CreateBatchOperationRequestDto batchDto)
      throws Exception {
    mockMvcManager.postRequest(
        PROCESS_INSTANCE_URL + "/batch-operation", batchDto, HttpStatus.SC_OK);
    searchTestRuleProvider.refreshSearchIndices();
  }

  public void waitForOperationFinished(final Long processInstanceKey) throws Exception {
    executeOneBatch();
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getOperationsByProcessInstanceAreCompletedCheck(), processInstanceKey);
  }

  public void waitForFlowNodeActive(final Long processInstanceKey, final String flowNodeId) {
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getFlowNodeIsActiveCheck(), processInstanceKey, flowNodeId);
  }

  public void completeTaskAndWaitForProcessFinish(
      final Long processInstanceKey,
      final String activityId,
      final String jobKey,
      final String payload) {
    ZeebeTestUtil.completeTask(camundaClient, jobKey, TestUtil.createRandomString(10), payload);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getFlowNodeIsCompletedCheck(), processInstanceKey, activityId);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstancesAreFinishedCheck(), Arrays.asList(processInstanceKey));
  }

  public OperateJ5Tester completeJob(final String jobKey) {
    ZeebeTestUtil.completeTask(camundaClient, jobKey, TestUtil.createRandomString(10), null);
    return this;
  }

  public void refreshSearchIndices() {
    searchTestRuleProvider.refreshSearchIndices();
  }

  public void waitUntilIncidentsAreActive(final Long processInstanceKey, final int count) {
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getIncidentsAreActiveCheck(), processInstanceKey, count);
  }

  public void waitUntilIncidentsInProcessAreActive(final String bpmnProcessId, final int count) {
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getIncidentsInProcessAreActiveCheck(), bpmnProcessId, count);
  }

  private int executeOneBatch() throws Exception {
    final List<Future<?>> futures = operationExecutor.executeOneBatch();
    // wait till all scheduled tasks are executed
    for (final Future f : futures) {
      f.get();
    }
    return 0; // return futures.size()
  }

  // Deprecated
  public Long startProcessAndWait(final String bpmnProcessId) {
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, bpmnProcessId, null);
    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessInstanceExistsCheck(), Arrays.asList(processInstanceKey));
    return processInstanceKey;
  }

  // Deprecated
  public Long deployProcessAndWait(final String classpathResource) {
    final Long processDefinitionKey =
        ZeebeTestUtil.deployProcess(camundaClient, null, classpathResource);

    searchTestRuleProvider.processAllRecordsAndWait(
        searchPredicates.getProcessIsDeployedCheck(), processDefinitionKey);

    return processDefinitionKey;
  }
}
