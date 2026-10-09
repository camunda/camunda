/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeSuspended;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class BatchOperationSuspendResumeProcessInstanceIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldSuspendRootInstanceWhenStateAndParentKeyFiltersAreIgnored() {
    // given
    final long processInstanceKey = startActiveRootInstance();

    // when - both filters would match nothing if they were applied
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceSuspend()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .state(ProcessInstanceState.COMPLETED)
                        .parentProcessInstanceKey(k -> k.exists(true)))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
  }

  @Test
  void shouldResumeRootInstanceWhenStateAndParentKeyFiltersAreIgnored() {
    // given
    final long processInstanceKey = startActiveRootInstance();
    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when - both filters would match nothing if they were applied
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceResume()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .state(ProcessInstanceState.ACTIVE)
                        .parentProcessInstanceKey(k -> k.exists(true)))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceKey),
        instances ->
            assertThat(instances)
                .singleElement()
                .extracting(ProcessInstance::getState)
                .isEqualTo(ProcessInstanceState.ACTIVE));
  }

  private static long startActiveRootInstance() {
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, processId + ".bpmn");
    final long processInstanceKey =
        startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    return processInstanceKey;
  }
}
