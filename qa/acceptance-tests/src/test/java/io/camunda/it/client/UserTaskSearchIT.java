/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.assertSorted;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static io.camunda.search.schema.config.IndexConfiguration.DEFAULT_VARIABLE_SIZE_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
class UserTaskSearchIT {
  private static Long userTaskKeyTaskAssigned;
  private static Long userTaskSubprocessKey;
  private static Long childUserTaskKey;
  private static Long parentProcessInstanceKey;
  private static Long childProcessInstanceKey;
  private static final String LARGE_VAR_NAME = "largeVariable";
  private static final String LARGE_VALUE = "b".repeat(DEFAULT_VARIABLE_SIZE_THRESHOLD + 10);

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {

    deployProcess("process", "simple.bpmn", "test", "", "");
    deployProcess("process-2", "simple-2.bpmn", "test-2", "group", "user");
    deployProcess("process-3", "simple-3.bpmn", "test-3", "", "", "30");
    deployProcessFromResourcePath("/process/bpm_variable_test.bpmn", "bpm_variable_test.bpmn");
    deployProcessFromResourcePath(
        "/process/bpmn_subprocess_case.bpmn", "bpmn_subprocess_case.bpmn");

    deployResource(camundaClient, "form/form.form");
    deployProcessFromResourcePath("/process/process_with_form.bpmn", "process_with_form.bpmn");
    deployProcessFromResourcePath("/process/job_worker_process.bpmn", "job_worker_process.bpmn");

    // Deploy parent-child process for testing rootProcessInstanceKey
    deployProcessFromResourcePath("/process/child_with_usertask.bpmn", "child_with_usertask.bpmn");
    deployProcessFromResourcePath(
        "/process/parent_with_child_usertask.bpmn", "parent_with_child_usertask.bpmn");

    startProcessInstance(camundaClient, "process");
    startProcessInstance(camundaClient, "process-2");
    startProcessInstance(camundaClient, "process");
    startProcessInstance(camundaClient, "process-3");
    startProcessInstance(camundaClient, "bpmProcessVariable");
    startProcessInstance(camundaClient, "processWithForm");
    startProcessInstance(
        camundaClient, "processWithSubProcess", Map.of(LARGE_VAR_NAME, LARGE_VALUE));
    startProcessInstance(
        camundaClient,
        "jobWorkerProcess"); // Start a Job Worker instance in order to validate if User Tasks
    // queries has the same result

    // Start parent process that calls child process with user task
    parentProcessInstanceKey =
        startProcessInstance(camundaClient, "parentWithChildUserTask").getProcessInstanceKey();

    waitForTasksBeingExported();

    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.elementId("TaskSub")).send().join();
    userTaskSubprocessKey = userTaskList.items().stream().findFirst().get().getUserTaskKey();

    // Get the child user task and its process instance key
    final var childUserTaskList =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.elementId("ChildUserTask"))
            .send()
            .join();
    final var childUserTask = childUserTaskList.items().stream().findFirst().get();
    childUserTaskKey = childUserTask.getUserTaskKey();
    childProcessInstanceKey = childUserTask.getProcessInstanceKey();
  }

  @Test
  public void shouldRetrieveTaskByLocalVariable() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.localVariables(Map.of("task02", "1")))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);

    // Validate that names "P1" and "P2" exist in the result
    assertThat(result.items().stream().map(UserTask::getName))
        .containsExactlyInAnyOrder("P1")
        .doesNotContain("P2");
  }

  @Test
  public void shouldRetrieveTaskByMapLocalVariable() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.localVariables(Map.of("task02", 1)))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);

    // Validate that names "P1" and "P2" exist in the result
    assertThat(result.items().stream().map(UserTask::getName))
        .containsExactlyInAnyOrder("P1")
        .doesNotContain("P2");
  }

  @Test
  public void shouldRetrieveTaskByProcessInstanceVariable() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceVariables(Map.of("task02", "1")))
            .send()
            .join();

    // Validate the size of the items
    assertThat(result.items()).hasSize(2);

    // Validate that names "P1" and "P2" exist in the result
    assertThat(result.items().stream().map(UserTask::getName))
        .containsExactlyInAnyOrder("P1", "P2");
  }

  @Test
  public void shouldRetrieveTaskByMapProcessInstanceVariable() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceVariables(Map.of("task02", 1)))
            .send()
            .join();

    // Validate the size of the items
    assertThat(result.items()).hasSize(2);

    // Validate that names "P1" and "P2" exist in the result
    assertThat(result.items().stream().map(UserTask::getName))
        .containsExactlyInAnyOrder("P1", "P2");
  }

  @Test
  public void shouldRetrieveTaskByProcessInstanceAndLocalVariable() {
    final Map<String, Object> variableValueFilter = Map.of("task02", 1);
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(
                f ->
                    f.processInstanceVariables(variableValueFilter)
                        .localVariables(variableValueFilter))
            .send()
            .join();

    // Validate the size of the items
    assertThat(result.items()).hasSize(1);

    // Validate that name "P1" exists in the result
    assertThat(result.items().stream().map(UserTask::getName)).containsExactlyInAnyOrder("P1");
  }

  @Test
  public void shouldHaveCorrectUserTaskName() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.elementId("form_process"))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getName()).isEqualTo("Form");
  }

  @Test
  public void shouldReturnCountWithPageZero() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.elementId("form_process"))
            .page(p -> p.limit(0))
            .send()
            .join();
    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.page().totalItems()).isOne();
  }

  @Test
  public void shouldUseUserTaskElementIdIfNameNotSet() {
    // when
    final var result =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.elementId("test-2")).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getName()).isEqualTo("test-2");
  }

  @Test
  public void shouldRetrieveVariablesFromUserTask() {
    final var resultUserTaskQuery =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.localVariables(Map.of("task02", "1")))
            .send()
            .join();

    // Retrieve userTaskKey that contains variables
    final var userTaskKey = resultUserTaskQuery.items().getFirst().getUserTaskKey();

    final var resultVariableQuery =
        camundaClient.newUserTaskVariableSearchRequest(userTaskKey).send().join();
    assertThat(resultVariableQuery.items()).hasSize(2);
  }

  @Test
  public void shouldRetrieveTaskByPriority() {
    // when
    final var result =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.priority(30)).send().join();

    // then
    assertThat(result.items()).hasSize(1);
  }

  @Test
  public void shouldRetrieveTaskByPriorityFilterGtLt() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.priority(b -> b.gt(29).lt(31)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
  }

  @Test
  public void shouldRetrieveTaskByPriorityFilterGteLte() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.priority(b -> b.gte(30).lte(30)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
  }

  @Test
  public void shouldRetrieveTaskByPriorityFilterIn() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.priority(b -> b.in(Integer.MAX_VALUE, 30)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
  }

  @Test
  public void shouldThrowExceptionIfVariableValueNullInConsumer() {

    // when
    final var exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newUserTaskSearchRequest()
                        .filter(
                            f -> f.processInstanceVariables(List.of(vf -> vf.name("process01"))))
                        .send()
                        .join())
            .actual();
    // then
    assertThat(exception.getMessage())
        .contains("Variable value cannot be null for variable 'process01'");
  }

  @Test
  public void shouldThrowExceptionIfVariableValueNullInMap() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("process01", null);

    // when
    final var exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newUserTaskSearchRequest()
                        .filter(f -> f.processInstanceVariables(variables))
                        .send()
                        .join())
            .actual();
    // then
    assertThat(exception.getMessage())
        .contains("Variable value cannot be null for variable 'process01'");
  }

  @Test
  public void shouldThrowExceptionIfVariableValueEmptyFilter() {
    // when
    final var exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newUserTaskSearchRequest()
                        .filter(
                            f ->
                                f.processInstanceVariables(
                                    List.of(vf -> vf.name("process01").value(v -> {}))))
                        .send()
                        .join())
            .actual();
    // then
    assertThat(exception.getMessage())
        .contains("Variable value cannot be null for variable 'process01'");
  }

  @Test
  public void shouldNotRetrieveTaskByInvalidVariableValue() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceVariables(Map.of("process01", "\"pVariable\"")))
            .send()
            .join();
    assertThat(result.items()).isEmpty();
  }

  @Test
  public void shouldNotRetrieveTaskIfNotAllLocalVariablesMatch() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.localVariables(Map.of("task02", "1", "task01", "\"test\"")))
            .send()
            .join();
    assertThat(result.items()).isEmpty();
  }

  @Test
  public void shouldRetrieveAllTasks() {
    final var result = camundaClient.newUserTaskSearchRequest().send().join();
    assertThat(result.items()).hasSize(9);
  }

  @Test
  public void shouldRetrieveTaskByLocalVariablesLike() {

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(
                f ->
                    f.localVariables(
                        List.of(vf -> vf.name("task01").value(v -> v.like("\"te*\"")))))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);
  }

  @Test
  public void shouldRetrieveTaskByLocalVariablesIn() {

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(
                f ->
                    f.localVariables(List.of(vf -> vf.name("task01").value(v -> v.in("\"test\"")))))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);
  }

  @Test
  public void shouldRetrieveTaskByAssignee() {
    final var result =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.assignee("demo")).send().join();
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getAssignee()).isEqualTo("demo");
    assertThat(result.items().getFirst().getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  public void shouldRetrieveTaskByAssigneeFilterIn() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.assignee(b -> b.in("not-found", "demo")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var first = result.items().getFirst();
    assertThat(first.getAssignee()).isEqualTo("demo");
    assertThat(first.getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  public void shouldRetrieveTaskByState() {
    final var resultCreated =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.CREATED))
            .send()
            .join();
    assertThat(resultCreated.items()).hasSize(8);
    resultCreated
        .items()
        .forEach(item -> assertThat(item.getState()).isEqualTo(UserTaskState.CREATED));

    final var resultCompleted =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .send()
            .join();
    assertThat(resultCompleted.items()).hasSize(1);
    resultCompleted
        .items()
        .forEach(item -> assertThat(item.getState()).isEqualTo(UserTaskState.COMPLETED));
  }

  @Test
  public void shouldRetrieveTaskByTaskDefinitionId() {
    final var result =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.elementId("test-2")).send().join();
    assertThat(result.items()).hasSize(1);
    result.items().forEach(item -> assertThat(item.getElementId()).isEqualTo("test-2"));
  }

  @Test
  public void shouldRetrieveTaskByBpmnDefinitionId() {
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.bpmnProcessId("process"))
            .send()
            .join();
    assertThat(result.items()).hasSize(2);
    result.items().forEach(item -> assertThat(item.getBpmnProcessId()).isEqualTo("process"));
  }

  @Test
  public void shouldRetrieveTaskByName() {
    final var result =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.name("P2")).send().join();
    assertThat(result.items()).hasSize(1);
    result.items().forEach(item -> assertThat(item.getName()).isEqualTo("P2"));
  }

  @Test
  public void shouldRetrieveTaskByCandidateGroup() {
    final var expectedGroup = List.of("group");
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.candidateGroup("group"))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);

    result.items().forEach(item -> assertThat(item.getCandidateGroups()).isEqualTo(expectedGroup));
  }

  @Test
  public void shouldRetrieveTaskByCandidateGroupFilter() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.candidateGroup(b -> b.like("grou?")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items()).extracting("candidateGroups").containsExactly(List.of("group"));
  }

  @Test
  public void shouldRetrieveTaskByCandidateUser() {
    final var expectedUser = List.of("user");
    final var result =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.candidateUser("user")).send().join();
    assertThat(result.items()).hasSize(1);

    result.items().forEach(item -> assertThat(item.getCandidateUsers()).isEqualTo(expectedUser));
  }

  @Test
  public void shouldRetrieveTaskByCandidateUserFilterIn() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.candidateUser(b -> b.in("not-found", "user")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items()).extracting("candidateUsers").containsExactly(List.of("user"));
  }

  @Test
  public void shouldValidatePagination() {
    final var result = camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();
    assertThat(result.items()).hasSize(1);
    final var key = result.items().getFirst().getUserTaskKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newUserTaskSearchRequest()
            .page(p -> p.after(result.page().endCursor()))
            .send()
            .join();

    assertThat(resultAfter.items()).hasSize(8);
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newUserTaskSearchRequest()
            .page(p -> p.before(resultAfter.page().startCursor()))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);
    assertThat(resultBefore.items().getFirst().getUserTaskKey()).isEqualTo(key);
  }

  @Test
  public void shouldSortTasksByCreationDateASC() {
    final var result =
        camundaClient.newUserTaskSearchRequest().sort(s -> s.creationDate().asc()).send().join();

    assertThat(result.items()).hasSize(9);

    // Assert that the creation date of item 0 is before item 1, and item 1 is before item 2
    final UserTask firstItem = result.items().get(0);
    assertThat(firstItem.getCreationDate()).isBefore(result.items().get(1).getCreationDate());
    assertThat(result.items().get(1).getCreationDate())
        .isBefore(result.items().get(2).getCreationDate());
  }

  @Test
  public void shouldSortTasksByStartDateDESC() {
    final var result =
        camundaClient.newUserTaskSearchRequest().sort(s -> s.creationDate().desc()).send().join();

    assertThat(result.items()).hasSize(9);

    assertThat(result.items().get(0).getCreationDate())
        .isAfterOrEqualTo(result.items().get(1).getCreationDate());
    assertThat(result.items().get(1).getCreationDate())
        .isAfterOrEqualTo(result.items().get(2).getCreationDate());
    assertThat(result.items().get(2).getCreationDate())
        .isAfterOrEqualTo(result.items().get(3).getCreationDate());
  }

  @Test
  void shouldSortTasksByName() {
    // when
    final var resultAsc =
        camundaClient.newUserTaskSearchRequest().sort(s -> s.name().asc()).send().join();
    final var resultDesc =
        camundaClient.newUserTaskSearchRequest().sort(s -> s.name().desc()).send().join();
    assertSorted(resultAsc, resultDesc, UserTask::getName);
  }

  @Test
  public void shouldRetrieveTaskByTenantId() {
    final var resultDefaultTenant =
        camundaClient.newUserTaskSearchRequest().filter(f -> f.tenantId("<default>")).send().join();
    assertThat(resultDefaultTenant.items()).hasSize(9);
    resultDefaultTenant
        .items()
        .forEach(item -> assertThat(item.getTenantId()).isEqualTo("<default>"));

    final var resultNonExistent =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.tenantId("<default123>"))
            .send()
            .join();
    assertThat(resultNonExistent.items()).isEmpty();
  }

  @Test
  public void retrievedTasksShouldIncludePriority() {
    final var resultDefaultPriority =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.bpmnProcessId("process-2"))
            .send()
            .join();
    assertThat(resultDefaultPriority.items()).hasSize(1);
    assertThat(resultDefaultPriority.items().getFirst().getPriority()).isEqualTo(50);

    final var resultDefinedPriority =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.bpmnProcessId("process-3"))
            .send()
            .join();
    assertThat(resultDefinedPriority.items()).hasSize(1);
    assertThat(resultDefinedPriority.items().getFirst().getPriority()).isEqualTo(30);
  }

  @Test
  void shouldGetUserTaskByKey() {
    // when
    final var result = camundaClient.newUserTaskGetRequest(userTaskKeyTaskAssigned).send().join();

    // then
    assertThat(result.getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  void shouldReturnRootProcessInstanceKeyDifferentFromProcessInstanceKeyForChildProcess() {
    // when - get the child user task from a call activity
    final var childUserTask = camundaClient.newUserTaskGetRequest(childUserTaskKey).send().join();

    // then - for child process, rootProcessInstanceKey should be the parent process instance key
    assertThat(childUserTask.getProcessInstanceKey()).isEqualTo(childProcessInstanceKey);
    assertThat(childUserTask.getRootProcessInstanceKey()).isEqualTo(parentProcessInstanceKey);
    assertThat(childUserTask.getRootProcessInstanceKey())
        .isNotEqualTo(childUserTask.getProcessInstanceKey());
  }

  @Test
  void shouldReturnUserTaskVariablesWithRootProcessInstanceKey() {
    // when - get variables from child user task
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(childUserTaskKey)
            .sort(s -> s.name().asc())
            .send()
            .join();

    // then - all variables should have rootProcessInstanceKey pointing to parent process
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            variable -> {
              assertThat(variable.getRootProcessInstanceKey())
                  .as(
                      "Variable '%s' should have rootProcessInstanceKey pointing to parent process",
                      variable.getName())
                  .isEqualTo(parentProcessInstanceKey);
              assertThat(variable.getProcessInstanceKey())
                  .as(
                      "Variable '%s' should have processInstanceKey pointing to child process",
                      variable.getName())
                  .isEqualTo(childProcessInstanceKey);
            });
  }

  @Test
  void shouldReturn404ForNotFoundUserTaskKey() {
    // when
    final long userTaskKey = new Random().nextLong();
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newUserTaskGetRequest(userTaskKey).send().join())
            .actual();
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .contains("User Task with key '%d' not found".formatted(userTaskKey));
  }

  @Test
  void shouldReturnFormByUserTaskKey() {
    // when
    final var userTaskList = camundaClient.newUserTaskSearchRequest().send().join();

    // filter userTask form the list when form is not null
    final var userTaskKeyWithForm =
        userTaskList.items().stream().filter(item -> item.getFormKey() != null).findFirst().get();

    final var result =
        camundaClient.newUserTaskGetFormRequest(userTaskKeyWithForm.getUserTaskKey()).send().join();

    // assert that the form key is the same as the form key of the user task
    assertThat(result.getFormKey()).isEqualTo(userTaskKeyWithForm.getFormKey());
  }

  @Test
  void shouldReturnNotFoundByUserTaskKeyWithNoForm() {
    // when
    final var userTaskList = camundaClient.newUserTaskSearchRequest().send().join();

    // filter userTask form the list when form is not null
    final var userTaskKeyWithNoForm =
        userTaskList.items().stream().filter(item -> item.getFormKey() == null).findFirst().get();

    final var result =
        camundaClient
            .newUserTaskGetFormRequest(userTaskKeyWithNoForm.getUserTaskKey())
            .send()
            .join();
    // then
    assertThat(result).hasAllNullFieldsOrProperties();
  }

  @Test
  void shouldFilterByElementInstanceKey() {
    // when
    final var userTaskList = camundaClient.newUserTaskSearchRequest().send().join();

    final var userTaskElementInstanceKey =
        userTaskList.items().stream().findFirst().get().getElementInstanceKey();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.elementInstanceKey(userTaskElementInstanceKey))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementInstanceKey())
        .isEqualTo(userTaskElementInstanceKey);
  }

  @Test
  void shouldReturnUserTaskVariablesWithSubProcessVariables() {
    // when
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(userTaskSubprocessKey)
            .sort(s -> s.name().asc())
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(4);
    assertThat(result.items().get(0).getName()).isEqualTo(LARGE_VAR_NAME);
    assertThat(result.items().get(1).getName()).isEqualTo("localVariable");
    assertThat(result.items().get(2).getName()).isEqualTo("processVariable");
    assertThat(result.items().get(3).getName()).isEqualTo("subProcessVariable");
  }

  @Test
  void shouldReturnUserTaskVariablesFilteredByNameEq() {
    // when
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(userTaskSubprocessKey)
            .filter(f -> f.name(b -> b.eq("localVariable")))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getName()).isEqualTo("localVariable");
  }

  @Test
  void shouldReturnUserTaskVariablesFilteredByNameLike() {
    // When
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(userTaskSubprocessKey)
            .filter(f -> f.name(b -> b.like("*rocess*")))
            .send()
            .join();

    // Then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(Variable::getName))
        .containsExactlyInAnyOrder("processVariable", "subProcessVariable");
  }

  @Test
  void shouldReturnUserTaskVariablesFilteredByIn() {
    // When
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(userTaskSubprocessKey)
            .filter(f -> f.name(b -> b.in(List.of("processVariable", "subProcessVariable"))))
            .send()
            .join();

    // Then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(Variable::getName))
        .containsExactlyInAnyOrder("processVariable", "subProcessVariable");
  }

  @Test
  void shouldReturnLargeUserTaskVariableUntruncated() {
    // when
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(userTaskSubprocessKey)
            .filter(f -> f.name(LARGE_VAR_NAME))
            .withFullValues()
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var resultVar = result.items().getFirst();
    assertThat(resultVar.getName()).isEqualTo(LARGE_VAR_NAME);
    assertThat(resultVar.isTruncated()).isFalse();
    assertThat(resultVar.getValue()).isEqualTo("\"" + LARGE_VALUE + "\"");
  }

  @Test
  void shouldReturnLargeUserTaskVariableTruncated() {
    // when
    final var result =
        camundaClient
            .newUserTaskVariableSearchRequest(userTaskSubprocessKey)
            .filter(f -> f.name(LARGE_VAR_NAME))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var resultVar = result.items().getFirst();
    assertThat(resultVar.getName()).isEqualTo(LARGE_VAR_NAME);
    assertThat(resultVar.isTruncated()).isTrue();
    assertThat(resultVar.getValue()).hasSizeLessThan(DEFAULT_VARIABLE_SIZE_THRESHOLD + 1);
  }

  @Test
  void shouldReturnUserTaskByCreationDateExists() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.creationDate(b -> b.exists(true)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(9);
  }

  @Test
  void shouldReturnUserTaskByCreationDateGt() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();

    final var userTaskCreationDateExample =
        userTaskList.items().stream().findFirst().get().getCreationDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.creationDate(b -> b.gt(userTaskCreationDateExample.minusSeconds(1))))
            .send()
            .join();

    // then all items the creation Date is greater than userTaskCreationDateExample - 1 second
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var creationDate = item.getCreationDate();
              assertThat(creationDate).isAfter(userTaskCreationDateExample.minusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCreationDateLt() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();

    final var userTaskCreationDateExample =
        userTaskList.items().stream().findFirst().get().getCreationDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.creationDate(b -> b.lt(userTaskCreationDateExample.plusSeconds(1))))
            .send()
            .join();

    // then all items the creation Date is less than userTaskCreationDateExample + 1 second
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var creationDate = item.getCreationDate();
              assertThat(creationDate).isBefore(userTaskCreationDateExample.plusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCreationDateGte() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();

    final var userTaskCreationDateExample =
        userTaskList.items().stream().findFirst().get().getCreationDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.creationDate(b -> b.gte(userTaskCreationDateExample.minusSeconds(1))))
            .send()
            .join();

    // then all items the creation Date is greater than or equal to userTaskCreationDateExample - 1
    // second
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var creationDate = item.getCreationDate();
              assertThat(creationDate)
                  .isAfterOrEqualTo(userTaskCreationDateExample.minusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCreationDateLte() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();

    final var userTaskCreationDateExample =
        userTaskList.items().stream().findFirst().get().getCreationDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.creationDate(b -> b.lte(userTaskCreationDateExample.plusSeconds(1))))
            .send()
            .join();

    // then all items the creation Date is less than or equal to userTaskCreationDateExample + 1
    // second
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var creationDate = item.getCreationDate();
              assertThat(creationDate)
                  .isBeforeOrEqualTo(userTaskCreationDateExample.plusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCreationDateEq() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();

    final var userTaskCreationDateExample =
        userTaskList.items().stream().findFirst().get().getCreationDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.creationDate(b -> b.eq(userTaskCreationDateExample)))
            .send()
            .join();

    // then all items have the exact creation Date
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var creationDate = item.getCreationDate();
              assertThat(creationDate).isEqualTo(userTaskCreationDateExample);
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGte() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(
                f -> f.completionDate(b -> b.gte(userTaskCompletionDateExample.minusSeconds(1))))
            .send()
            .join();

    // then all items the completion Date is greater than or equal to userTaskCompletionDateExample
    // - 1 second
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate)
                  .isAfterOrEqualTo(userTaskCompletionDateExample.minusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateLte() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.completionDate(b -> b.lte(userTaskCompletionDateExample.plusSeconds(1))))
            .send()
            .join();

    // then all items the completion Date is less than or equal to userTaskCompletionDateExample + 1
    // second
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate)
                  .isBeforeOrEqualTo(userTaskCompletionDateExample.plusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGteLte() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(
                f ->
                    f.completionDate(
                        b ->
                            b.gte(userTaskCompletionDateExample.minusDays(1))
                                .lte(userTaskCompletionDateExample.plusDays(1))))
            .send()
            .join();

    // then all items have the completion Date within the range
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate)
                  .isBetween(
                      userTaskCompletionDateExample.minusDays(1),
                      userTaskCompletionDateExample.plusDays(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGtLt() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(
                f ->
                    f.completionDate(
                        b ->
                            b.gt(userTaskCompletionDateExample.minusDays(1))
                                .lt(userTaskCompletionDateExample.plusDays(1))))
            .send()
            .join();

    // then all items have the completion Date strictly within the range (exclusive)
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate)
                  .isAfter(userTaskCompletionDateExample.minusDays(1))
                  .isBefore(userTaskCompletionDateExample.plusDays(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateExists() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.completionDate(b -> b.exists(true)))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              assertThat(item.getState()).isEqualTo(UserTaskState.COMPLETED);
              assertThat(item.getCompletionDate()).isNotNull();
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateNotExists() {
    // when
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.completionDate(b -> b.exists(false)))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result.items().forEach(item -> assertThat(item.getCompletionDate()).isNull());
  }

  @Test
  void shouldReturnUserTaskByCompletionDateEq() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.completionDate(b -> b.eq(userTaskCompletionDateExample)))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate).isEqualTo(userTaskCompletionDateExample);
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateGt() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.completionDate(b -> b.gt(userTaskCompletionDateExample.minusSeconds(1))))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate).isAfter(userTaskCompletionDateExample.minusSeconds(1));
            });
  }

  @Test
  void shouldReturnUserTaskByCompletionDateLt() {
    // when
    final var userTaskListComplete =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.state(UserTaskState.COMPLETED))
            .page(p -> p.limit(1))
            .send()
            .join();

    final var userTaskCompletionDateExample =
        userTaskListComplete.items().stream().findFirst().get().getCompletionDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.completionDate(b -> b.lt(userTaskCompletionDateExample.plusSeconds(1))))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var completionDate = item.getCompletionDate();
              assertThat(completionDate).isBefore(userTaskCompletionDateExample.plusSeconds(1));
            });
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newUserTaskSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getUserTaskKey();

    final var resultSearchFrom =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(2).from(2)).send().join();

    // then
    assertThat(resultSearchFrom.items()).hasSize(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getUserTaskKey())
        .isEqualTo(thirdKey);
  }

  @Test
  void shouldRetrieveTaskByDueDateRange() {
    // when
    final var now = OffsetDateTime.now(ZoneId.of("UTC"));
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.dueDate(b -> b.gt(now.minusDays(2)).lt(now.plusDays(2))))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var dueDate = item.getDueDate();
              assertThat(dueDate).isAfter(now.minusDays(2)).isBefore(now.plusDays(2));
            });
  }

  @Test
  void shouldRetrieveTaskByDueDateEquals() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();
    final var dueDateExample = userTaskList.items().getFirst().getDueDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.dueDate(b -> b.eq(dueDateExample)))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var dueDate = item.getDueDate();
              assertThat(dueDate).isEqualTo(dueDateExample);
            });
  }

  @Test
  void shouldRetrieveTaskByFollowUpDateEquals() {
    // when
    final var userTaskList =
        camundaClient.newUserTaskSearchRequest().page(p -> p.limit(1)).send().join();
    final var followUpDateExample = userTaskList.items().get(0).getFollowUpDate();

    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.followUpDate(b -> b.eq(followUpDateExample)))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var followUpDate = item.getFollowUpDate();
              assertThat(followUpDate).isEqualTo(followUpDateExample);
            });
  }

  @Test
  void shouldRetrieveTaskByFollowUpDateRange() {
    // when
    final var now = OffsetDateTime.now(ZoneId.of("UTC"));
    final var result =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.followUpDate(b -> b.gte(now.minusDays(5)).lte(now.plusDays(5))))
            .send()
            .join();

    // then
    assertThat(result.items()).isNotEmpty();
    result
        .items()
        .forEach(
            item -> {
              final var followUpDate = item.getFollowUpDate();
              assertThat(followUpDate)
                  .isAfterOrEqualTo(now.minusDays(5))
                  .isBeforeOrEqualTo(now.plusDays(5));
            });
  }

  private static void deployProcess(
      final String processId,
      final String resourceName,
      final String userTaskName,
      final String candidateGroup,
      final String candidateUser) {
    final DateTimeFormatter dateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);
    final LocalDateTime dayBefore = now.minusDays(1);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(userTaskName)
                .zeebeUserTask()
                .zeebeDueDate(dayBefore.format(dateFormat))
                .zeebeFollowUpDate(dayBefore.format(dateFormat))
                .zeebeCandidateGroups(candidateGroup)
                .zeebeCandidateUsers(candidateUser)
                .endEvent()
                .done(),
            resourceName)
        .send()
        .join();
  }

  private static void deployProcess(
      final String processId,
      final String resourceName,
      final String userTaskName,
      final String candidateGroup,
      final String candidateUser,
      final String priority) {
    final DateTimeFormatter dateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);
    final LocalDateTime dayBefore = now.minusDays(1);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(userTaskName)
                .zeebeTaskPriority(priority)
                .zeebeUserTask()
                .zeebeDueDate(dayBefore.format(dateFormat))
                .zeebeFollowUpDate(dayBefore.format(dateFormat))
                .zeebeCandidateGroups(candidateGroup)
                .zeebeCandidateUsers(candidateUser)
                .endEvent()
                .done(),
            resourceName)
        .send()
        .join();
  }

  private static void deployProcessFromResourcePath(
      final String resource, final String resourceName) {
    final InputStream process = UserTaskSearchIT.class.getResourceAsStream(resource);
    deployProcessAndWaitForIt(camundaClient, Bpmn.readModelFromStream(process), resourceName);
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUserTaskSearchRequest().send().join();
              assertThat(result.items()).hasSize(9);
              userTaskKeyTaskAssigned = result.items().getFirst().getUserTaskKey();
            });

    camundaClient
        .newAssignUserTaskCommand(userTaskKeyTaskAssigned)
        .assignee("demo")
        .action("assignee")
        .send()
        .join();

    camundaClient.newCompleteUserTaskCommand(userTaskKeyTaskAssigned).send().join();

    Awaitility.await("should export Assigned task and Completed to ElasticSearch")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.assignee("demo"))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
              final var resultComplete =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.state(UserTaskState.COMPLETED))
                      .send()
                      .join();
              assertThat(resultComplete.items()).hasSize(1);
            });
  }

  /**
   * TODO: RDBMS returns the date as ISO String, there are ongoing discussion in which format the
   * sort value should be returned for dates.
   * https://camunda.slack.com/archives/C06UKS51QV9/p1738838925251429
   */
  private static String convertDateIfNeeded(final String millisOrIsoDate) {
    if (millisOrIsoDate.contains("T")) {
      return Long.toString(OffsetDateTime.parse(millisOrIsoDate).toInstant().toEpochMilli());
    } else {
      return millisOrIsoDate;
    }
  }
}
