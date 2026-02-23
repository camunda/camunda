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
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class JobSearchIT {

  private static CamundaClient camundaClient;
  private static Job taskABpmnJob;

  @BeforeAll
  static void beforeAll() {

    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("form/job_search_process.form")
        .send()
        .join();
    final Process process1 =
        deployProcessAndWaitForIt(camundaClient, "process/job_search_process.bpmn");
    startProcessInstance(camundaClient, process1.getBpmnProcessId());
    final Process process2 =
        deployProcessAndWaitForIt(camundaClient, "process/service_tasks_v1.bpmn");
    startProcessInstance(camundaClient, process2.getBpmnProcessId());
    waitForProcessInstancesToStart(camundaClient, 2);

    // Wait until the total number of jobs in the system reaches 1
    waitUntilNewJobHasBeenCreated(2);

    final var executionStartListenerJob =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f -> f.type("taskAExecutionListener").listenerEventType(ListenerEventType.START))
            .send()
            .join()
            .items()
            .getFirst();

    camundaClient.newCompleteCommand(executionStartListenerJob.getJobKey()).send().join();

    // Wait until the total number of jobs in the system reaches 2
    waitUntilNewJobHasBeenCreated(3);

    final var taskABpmnJob =
        camundaClient
            .newActivateJobsCommand()
            .jobType("taskABpmn")
            .maxJobsToActivate(1)
            .workerName("worker1")
            .timeout(Duration.ofSeconds(3))
            .send()
            .join()
            .getJobs()
            .getFirst();

    camundaClient.newCompleteCommand(taskABpmnJob.getKey()).send().join();

    // Wait until the total number of jobs in the system reaches 3
    waitUntilNewJobHasBeenCreated(4);

    final var executionEndListenerJob =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type("taskAExecutionListener").listenerEventType(ListenerEventType.END))
            .send()
            .join()
            .items()
            .getFirst();

    camundaClient.newCompleteCommand(executionEndListenerJob.getJobKey()).send().join();

    waitForSingleUserTaskWithCreatedState();
    final var userTask =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.CREATED))
            .send()
            .join()
            .items()
            .getFirst();

    camundaClient
        .newAssignUserTaskCommand(userTask.getUserTaskKey())
        .assignee("testAssignee")
        .send();

    // Wait until the total number of jobs in the system reaches 4
    waitUntilNewJobHasBeenCreated(5);

    final var userTaskListenerAssigningJob1 =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type("taskBTaskListener").listenerEventType(ListenerEventType.ASSIGNING))
            .send()
            .join()
            .items()
            .getFirst();

    camundaClient
        .newCompleteCommand(userTaskListenerAssigningJob1.getJobKey())
        .withResult(r -> r.forUserTask().deny(true, "test denied reason"))
        .send()
        .join();

    waitForSingleUserTaskWithCreatedState();
    camundaClient
        .newAssignUserTaskCommand(userTask.getUserTaskKey())
        .assignee("testAssignee")
        .send();

    // Wait until the total number of jobs in the system reaches 5
    waitUntilNewJobHasBeenCreated(6);

    final var userTaskListenerAssigningJob2 =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f ->
                    f.type("taskBTaskListener")
                        .state(JobState.CREATED)
                        .listenerEventType(ListenerEventType.ASSIGNING))
            .send()
            .join()
            .items()
            .getFirst();

    camundaClient.newCompleteCommand(userTaskListenerAssigningJob2.getJobKey()).send().join();

    waitForSingleUserTaskWithCreatedState();
    camundaClient
        .newCompleteUserTaskCommand(userTask.getUserTaskKey())
        .variable("name", "test")
        .send();

    // Wait until the total number of jobs in the system reaches 6
    waitUntilNewJobHasBeenCreated(7);

    final var taskCBpmnJob =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type("taskCBpmn"))
            .send()
            .join()
            .items()
            .getFirst();

    camundaClient
        .newThrowErrorCommand(taskCBpmnJob.getJobKey())
        .errorCode("400")
        .errorMessage("test error")
        .send()
        .join();

    waitUntilErrorIsThrown();

    JobSearchIT.taskABpmnJob =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type(p -> p.eq("taskABpmn")))
            .send()
            .join()
            .items()
            .getFirst();
  }

  @Test
  void shouldReturnAllJobsByDefault() {
    // given
    final var result = camundaClient.newJobSearchRequest().send().join();
    // then
    assertThat(result.items())
        .hasSize(7)
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(
            List.of(
                "taskABpmn",
                "taskAExecutionListener",
                "taskBTaskListener",
                "taskCBpmn",
                "taskAExecutionListener",
                "taskBTaskListener",
                "taskA"));
  }

  @Test
  void shouldReturnActivatedJobWithKindAndListenerEventType() {
    // given
    final var result =
        camundaClient
            .newActivateJobsCommand()
            .jobType("taskA")
            .maxJobsToActivate(1)
            .workerName("worker2")
            .timeout(Duration.ofSeconds(3))
            .send()
            .join()
            .getJobs()
            .getFirst();
    // then
    assertThat(result.getType()).isEqualTo("taskA");
    assertThat(result.getWorker()).isEqualTo("worker2");
    assertThat(result.getKind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(result.getListenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
  }

  @Test
  void shouldSearchJobByKey() {
    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.jobKey(taskABpmnJob.getJobKey()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(taskABpmnJob.getJobKey());
    assertThat(result.items().getFirst().getRootProcessInstanceKey())
        .isEqualTo(taskABpmnJob.getProcessInstanceKey());
  }

  @Test
  void shouldSearchJobByType() {
    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type(o -> o.eq("taskABpmn")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskABpmn");
  }

  @Test
  void shouldSearchJobByWorker() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.worker(o -> o.eq("worker1")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskABpmn");
    assertThat(result.items().getFirst().getWorker()).isEqualTo("worker1");
  }

  @Test
  void shouldSearchJobByState() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.state(o -> o.eq(JobState.COMPLETED)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(5);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(
            List.of(
                "taskABpmn",
                "taskAExecutionListener",
                "taskBTaskListener",
                "taskAExecutionListener",
                "taskBTaskListener"));
    assertThat(result.items())
        .extracting(Job::getState)
        .allMatch(s -> s.equals(JobState.COMPLETED));
  }

  @Test
  void shouldSearchJobByKind() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.kind(o -> o.eq(JobKind.EXECUTION_LISTENER)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(Job::getType)
        .allMatch(t -> t.equals("taskAExecutionListener"));
    assertThat(result.items())
        .extracting(Job::getKind)
        .allMatch(k -> k.equals(JobKind.EXECUTION_LISTENER));
  }

  @Test
  void shouldSearchJobByListenerEventType() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.listenerEventType(o -> o.eq(ListenerEventType.START)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskAExecutionListener");
    assertThat(result.items().getFirst().getListenerEventType()).isEqualTo(ListenerEventType.START);
  }

  @Test
  void shouldSearchJobByProcessDefinitionId() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processDefinitionId(o -> o.eq(taskABpmnJob.getProcessDefinitionId())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(6);
    assertThat(result.items())
        .extracting(Job::getProcessDefinitionId)
        .allMatch(p -> p.equals(taskABpmnJob.getProcessDefinitionId()));
  }

  @Test
  void shouldSearchJobByProcessDefinitionKey() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processDefinitionKey(o -> o.eq(taskABpmnJob.getProcessDefinitionKey())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(6);

    assertThat(result.items())
        .extracting(Job::getProcessDefinitionKey)
        .allMatch(p -> p.equals(taskABpmnJob.getProcessDefinitionKey()));
  }

  @Test
  void shouldSearchJobByProcessInstanceKey() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(o -> o.eq(taskABpmnJob.getProcessInstanceKey())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(6);
    assertThat(result.items())
        .extracting(Job::getProcessInstanceKey)
        .allMatch(p -> p.equals(taskABpmnJob.getProcessInstanceKey()));
  }

  @Test
  void shouldSearchJobByElementId() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.elementId(o -> o.eq(taskABpmnJob.getElementId())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting(Job::getElementId)
        .allMatch(e -> e.equals(taskABpmnJob.getElementId()));
  }

  @Test
  void shouldSearchJobByElementInstanceKey() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.elementInstanceKey(o -> o.eq(taskABpmnJob.getElementInstanceKey())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting(Job::getElementInstanceKey)
        .allMatch(e -> e.equals(taskABpmnJob.getElementInstanceKey()));
  }

  @Test
  void shouldSearchJobByTenantId() {
    // given
    final var tenantId = "<default>";

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.tenantId(o -> o.eq(tenantId)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(7);
    assertThat(result.items())
        .extracting(Job::getTenantId)
        .allMatch(t -> t.equals(taskABpmnJob.getTenantId()));
  }

  @Test
  void shouldSearchJobByDeadline() {
    // given
    final var deadline = taskABpmnJob.getDeadline();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.deadline(o -> o.eq(deadline)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(taskABpmnJob.getJobKey());
  }

  @Test
  void shouldSearchJobByDeniedReason() {
    // given
    final var deniedReason = "test denied reason";

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.deniedReason(o -> o.eq(deniedReason)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDeniedReason()).isEqualTo(deniedReason);
  }

  @Test
  void shouldSearchJobByEndTime() {
    // given
    final var endTime = taskABpmnJob.getEndTime();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.endTime(o -> o.eq(endTime)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(taskABpmnJob.getJobKey());
  }

  @Test
  void shouldSearchJobByErrorCode() {
    // given
    final var errorCode = "400";

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.errorCode(p -> p.eq(errorCode)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getErrorCode()).isEqualTo(errorCode);
  }

  @Test
  void shouldSearchJobByErrorMessage() {
    // given
    final var errorMessage = "test error";

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.errorMessage(p -> p.eq(errorMessage)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getErrorMessage()).isEqualTo(errorMessage);
  }

  @Test
  void shouldSearchJobByHasFailedWithRetriesLeft() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.hasFailedWithRetriesLeft(true))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().hasFailedWithRetriesLeft()).isTrue();
  }

  @Test
  void shouldSearchJobByIsDenied() {

    // when
    final var result =
        camundaClient.newJobSearchRequest().filter(f -> f.isDenied(true)).send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().isDenied()).isTrue();
  }

  @Test
  void shouldSearchJobByRetries() {
    // given
    final var retries = 3;

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.retries(o -> o.gte(retries)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(7);
    assertThat(result.items().getFirst().getRetries()).isEqualTo(retries);
  }

  @Test
  void shouldSortJobsByKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getJobKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.jobKey().asc()).send().join();

    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.jobKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getJobKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());

    assertThat(resultDesc.items())
        .extracting(Job::getJobKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByType() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getType)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.type().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.type().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByWorker() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getWorker)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.worker().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.worker().desc()).send().join();

    // then
    // note: For OracleDB empty strings are treated as NULLs, so we need to handle nulls in sorting
    assertThat(resultAsc.items())
        .extracting(Job::getWorker)
        .containsExactlyElementsOf(
            all.stream().sorted(Comparator.nullsLast(Comparator.naturalOrder())).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getWorker)
        .containsExactlyElementsOf(
            all.stream().sorted(Comparator.nullsFirst(Comparator.reverseOrder())).toList());
  }

  @Test
  void shouldSortJobsByState() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getState)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.state().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getState)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getState)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByKind() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getKind)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.kind().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.kind().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getKind).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getKind).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByListenerEventType() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getListenerEventType)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.listenerEventType().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.listenerEventType().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getListenerEventType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getListenerEventType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByProcessDefinitionId() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getProcessDefinitionId)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.processDefinitionId().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.processDefinitionId().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getProcessDefinitionId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getProcessDefinitionId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByProcessDefinitionKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getProcessDefinitionKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.processDefinitionKey().asc()).send().join();
    final var resultDesc =
        camundaClient
            .newJobSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getProcessDefinitionKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getProcessDefinitionKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByProcessInstanceKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getProcessInstanceKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.processInstanceKey().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.processInstanceKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getProcessInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getProcessInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByElementId() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getElementId)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementId().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementId().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getElementId).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getElementId).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter((Objects::nonNull)).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByElementInstanceKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getElementInstanceKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementInstanceKey().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementInstanceKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getElementInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getElementInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByTenantId() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getTenantId)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.tenantId().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.tenantId().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getTenantId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getTenantId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByDeadline() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getDeadline)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.deadline().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.deadline().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getDeadline).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getDeadline).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByDeniedReason() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getDeniedReason)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.deniedReason().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.deniedReason().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getDeniedReason).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getDeniedReason).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByEndTime() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getEndTime)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.endTime().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.endTime().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getEndTime).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getEndTime).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByErrorCode() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getErrorCode)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.errorCode().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.errorCode().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getErrorCode).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getErrorCode).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByErrorMessage() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getErrorMessage)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.errorMessage().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.errorMessage().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::getErrorMessage).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::getErrorMessage).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByHasFailedWithRetriesLeft() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::hasFailedWithRetriesLeft)
            .toList();
    // when
    final var resultAsc =
        camundaClient
            .newJobSearchRequest()
            .sort(s -> s.hasFailedWithRetriesLeft().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newJobSearchRequest()
            .sort(s -> s.hasFailedWithRetriesLeft().desc())
            .send()
            .join();

    // then
    assertThat(
            resultAsc.items().stream().map(Job::hasFailedWithRetriesLeft).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(
            resultDesc.items().stream().map(Job::hasFailedWithRetriesLeft).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByIsDenied() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::isDenied)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.isDenied().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.isDenied().desc()).send().join();

    // then
    assertThat(resultAsc.items().stream().map(Job::isDenied).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(Job::isDenied).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByRetries() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getRetries)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.retries().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.retries().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getRetries)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getRetries)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSearchByFromLimit() {
    // given
    final var allJobs =
        camundaClient
            .newJobSearchRequest()
            .sort(s -> s.jobKey().asc())
            .send()
            .join()
            .items()
            .stream()
            .map(Job::getJobKey)
            .toList();
    final int limit = 2;

    // when
    final var result = camundaClient.newJobSearchRequest().page(p -> p.limit(limit)).send().join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(Job::getJobKey).containsAll(allJobs.subList(0, 2));
  }

  @Test
  void shouldSearchAfterSecondItem() {
    // given
    final var allJobs = camundaClient.newJobSearchRequest().send().join().items();
    final var thirdJobKey = allJobs.get(2).getJobKey();

    final var result = camundaClient.newJobSearchRequest().page(p -> p.limit(2)).send().join();

    // when
    final var resultSearchAfter =
        camundaClient
            .newJobSearchRequest()
            .page(p -> p.limit(1).after(result.page().endCursor()))
            .send()
            .join();

    // then
    assertThat(resultSearchAfter.items().stream().findFirst().get().getJobKey())
        .isEqualTo(thirdJobKey);
  }

  @Test
  void shouldSearchBeforeSecondItem() {
    // given
    final var allJobs = camundaClient.newJobSearchRequest().send().join().items();
    final var firstJobKey = allJobs.getFirst().getJobKey();

    final var result =
        camundaClient.newJobSearchRequest().page(p -> p.limit(2).from(1)).send().join();

    // when
    final var resultSearchBefore =
        camundaClient
            .newJobSearchRequest()
            .page(p -> p.limit(1).before(result.page().startCursor()))
            .send()
            .join();

    // then
    assertThat(resultSearchBefore.items().stream().findFirst().get().getJobKey())
        .isEqualTo(firstJobKey);
  }

  @Test
  void shouldSearchByCreationTime() {
    // given
    final var creationTime = taskABpmnJob.getCreationTime();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.creationTime(o -> o.eq(creationTime)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getCreationTime())
        .isEqualTo(taskABpmnJob.getCreationTime());
  }

  @Test
  void shouldSearchByLastUpdateTime() {
    // given
    final var lastUpdateTime = taskABpmnJob.getLastUpdateTime();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.lastUpdateTime(o -> o.eq(lastUpdateTime)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getLastUpdateTime())
        .isEqualTo(taskABpmnJob.getLastUpdateTime());
  }

  private static void waitUntilNewJobHasBeenCreated(final int expectedCount) {
    Awaitility.await("should wait until job has been created")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newJobSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedCount);
            });
  }

  private static void waitForSingleUserTaskWithCreatedState() {
    await("should wait until user task with state='CREATED' found")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.state(UserTaskState.CREATED))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(1);
            });
  }

  private static void waitUntilErrorIsThrown() {
    await("should wait until error is thrown")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newJobSearchRequest()
                      .filter(f -> f.type(o -> o.eq("taskCBpmn")).state(JobState.ERROR_THROWN))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(1);
            });
  }
}
