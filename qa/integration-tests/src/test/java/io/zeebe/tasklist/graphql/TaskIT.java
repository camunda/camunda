/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.graphql;

import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.util.ElasticsearchChecks.TestCheck;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class TaskIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String GET_TASK_QUERY_PATTERN =
      "{task(id: \"%s\"){id name workflowName creationTime completionTime assignee {username} variables {name} taskState}}";
  public static final String TASK_RESULT_PATTERN =
      "{id name assignee {username firstname lastname}}";
  public static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Before
  public void before() {
    super.before();
    try {
      FieldSetter.setField(
          taskMutationResolver,
          TaskMutationResolver.class.getDeclaredField("zeebeClient"),
          super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @Test
  public void shouldReturnAllTasks() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final GraphQLResponse response =
        tester
            .having()
            .createAndDeploySimpleWorkflow(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .workflowIsDeployed()
            .and()
            .startWorkflowInstance(bpmnProcessId)
            .startWorkflowInstance(bpmnProcessId)
            .startWorkflowInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 3; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertNotNull(response.get(taskJsonPath + ".id"));

      // workflow does not contain task name and workflow name
      assertEquals(flowNodeBpmnId, response.get(taskJsonPath + ".name"));
      assertEquals(bpmnProcessId, response.get(taskJsonPath + ".workflowName"));

      assertNotNull(response.get(taskJsonPath + ".creationTime"));
      assertNull(response.get(taskJsonPath + ".completionTime"));
      assertEquals(TaskState.CREATED.name(), response.get(taskJsonPath + ".taskState"));
      assertNull(response.get(taskJsonPath + ".assignee"));
      assertEquals("0", response.get(taskJsonPath + ".variables.length()"));
    }
    assertSorting(response);
  }

  private void assertSorting(GraphQLResponse response) {
    final List<TaskDTO> tasks = Arrays.asList(response.get("$.data.tasks", TaskDTO[].class));
    final Comparator<TaskDTO> comparator =
        Comparator.comparing(TaskDTO::getCreationTime).reversed();
    assertThat(tasks).isSortedAccordingTo(comparator);
  }

  @Test
  public void shouldReturnCompletedTask() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final GraphQLResponse response =
        tester
            .createAndDeploySimpleWorkflow(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .workflowIsDeployed()
            .and()
            .startWorkflowInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .claimAndCompleteHumanTask(flowNodeBpmnId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertNotNull(response.get("$.data.tasks[0].id"));
    assertEquals(flowNodeBpmnId, response.get("$.data.tasks[0].name"));
    assertEquals(bpmnProcessId, response.get("$.data.tasks[0].workflowName"));
    assertNotNull(response.get("$.data.tasks[0].creationTime"));
    assertNotNull(response.get("$.data.tasks[0].completionTime"));
    assertEquals(TaskState.COMPLETED.name(), response.get("$.data.tasks[0].taskState"));
    assertNotNull(response.get("$.data.tasks[0].assignee.username"));
    assertEquals("0", response.get("$.data.tasks[0].variables.length()"));
  }

  @Test
  public void shouldNotImportWrongJobType() throws IOException {
    // having
    final String bpmnProcessId = "testProcess";
    final String wrongFlowNodeBpmnId = "taskA";
    final String wrongJobType = "serviceTask";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .serviceTask(wrongFlowNodeBpmnId)
            .zeebeJobType(wrongJobType)
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .having()
            .deployWorkflow(workflow, bpmnProcessId + ".bpmn")
            .waitUntil()
            .workflowIsDeployed()
            .and()
            .startWorkflowInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(wrongFlowNodeBpmnId) // this waiting must time out
            .when()
            .getAllTasks();
    // then
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.tasks.length()"));
  }

  @Test
  public void shouldReturnAllOpenTasks() throws IOException {
    final List<TaskDTO> createdTasks =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, 3)
            .and()
            .when()
            .getCreatedTasks();

    // then
    assertEquals(2, createdTasks.size());
    createdTasks.forEach(t -> assertEquals(TaskState.CREATED, t.getTaskState()));
  }

  @Test
  public void shouldReturnAllCompletedTasks() throws IOException {
    final GraphQLResponse response =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, 3)
            .when()
            .getCompletedTasks();
    // then
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 3; i++) {
      assertEquals(
          TaskState.COMPLETED.name(), response.get(String.format("$.data.tasks[%d].taskState", i)));
    }
  }

  @Test
  public void shouldReturnUnclaimedTasks() throws IOException {
    // when #1
    final List<TaskDTO> tasks =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 3, 0)
            .when()
            .getTasksByQuery("{tasks(query: {assigned: false}) {id}}");
    // then #1
    assertEquals(3, tasks.size());

    // when #2
    final List<TaskDTO> tasksAfterOneClaimed =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, tasks.get(0).getId()))
            .and()
            .waitFor(1000)
            .then() // then #2
            .getTasksByQuery("{tasks(query: {assigned: false}) {id}}");
    assertEquals(2, tasksAfterOneClaimed.size());
  }

  @Test
  public void shouldReturnClaimedByUser() throws IOException {
    List<TaskDTO> tasks =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, 1)
            .when()
            .getTasksByQuery("{tasks(query: {assignee: \"demo\", state: CREATED}) {id}}");
    assertEquals(0, tasks.size());

    tasks = tester.getCreatedTasks();

    tasks =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, tasks.get(0).getId()))
            .and()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, tasks.get(1).getId()))
            .then()
            .waitFor(1000)
            .getTasksByQuery("{tasks(query: { assignee: \"demo\", state: CREATED}) {id}}");
    assertEquals(2, tasks.size());
  }

  @Test
  public void shouldReturnTasksClaimedByDifferentUsers() throws IOException {
    // create users
    final UserDTO joe = new UserDTO().setUsername("joe").setFirstname("Joe").setLastname("Doe");
    final UserDTO jane = new UserDTO().setUsername("jane").setFirstname("Jane").setLastname("Doe");
    final UserDTO demo = new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User");
    // create tasks
    final List<TaskDTO> createdTasks =
        tester
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 5, 2)
            .then()
            .getCreatedTasks();
    List<TaskDTO> unclaimedTasks = tester.getTasksByQuery("{tasks(query: {assigned: false}) {id}}");
    assertEquals(5, unclaimedTasks.size());
    // when
    setCurrentUser(joe);
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(2).getId()));
    setCurrentUser(jane);
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(1).getId()));
    setCurrentUser(demo);
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(4).getId()));

    tester.waitFor(2000);
    unclaimedTasks =
        tester.getTasksByQuery("{tasks(query: {assigned: false, state: CREATED}) {id}}");
    assertEquals(2, unclaimedTasks.size());

    final List<TaskDTO> joesTasks =
        tester.getTasksByQuery("{tasks(query: {assignee: \"joe\", state: CREATED}) {id}}");
    assertEquals(1, joesTasks.size());
    assertEquals(createdTasks.get(2).getId(), joesTasks.get(0).getId());

    final List<TaskDTO> janesTasks =
        tester.getTasksByQuery("{tasks(query: {assignee: \"jane\", state: CREATED}) {id}}");
    assertEquals(1, janesTasks.size());
    assertEquals(createdTasks.get(1).getId(), janesTasks.get(0).getId());

    final List<TaskDTO> demoTasks =
        tester.getTasksByQuery("{tasks(query: {assignee: \"demo\", state: CREATED}) {id}}");
    assertEquals(1, demoTasks.size());
    assertEquals(createdTasks.get(4).getId(), demoTasks.get(0).getId());
  }
  /** Tests variables loader. */
  @Test
  public void shouldReturnManyTasksWithVariables() throws IOException {
    // having
    createCreatedAndCompletedTasksWithVariables("testProcess_", "task_");

    // when
    final GraphQLResponse response = tester.getAllTasks();

    // then
    assertEquals("6", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 6; i++) {
      final String flowNodeBpmnId = response.get("$.data.tasks[" + i + "].name");
      final String variableValue = response.get("$.data.tasks[" + i + "].variables[0].value");
      assertEquals("\"" + flowNodeBpmnId + "\"", variableValue);
    }
  }

  private void createCreatedAndCompletedTasksWithVariables(
      String bpmnProcessIdPattern, String flowNodeBpmnIdPattern) {
    for (int i = 0; i < 6; i++) {
      final String bpmnProcessId = bpmnProcessIdPattern + i;
      final String flowNodeBpmnId = flowNodeBpmnIdPattern + i;
      final BpmnModelInstance workflow =
          Bpmn.createExecutableProcess(bpmnProcessId)
              .startEvent()
              .serviceTask(flowNodeBpmnId)
              .zeebeJobType(tasklistProperties.getImporter().getJobType())
              .endEvent()
              .done();
      tester
          .deployWorkflow(workflow, workflow + ".bpmn")
          .waitUntil()
          .workflowIsDeployed()
          .and()
          .startWorkflowInstance(bpmnProcessId, "{\"flowNodeBpmnId\": \"" + flowNodeBpmnId + "\"}")
          .waitUntil()
          .taskIsCreated(flowNodeBpmnId);
      if (i % 2 == 0) {
        tester.claimAndCompleteHumanTask(flowNodeBpmnId);
      }
    }
  }

  @Test
  public void shouldReturnWorkflowAndTaskName() throws IOException {
    // having
    final String workflowName = "Test process name";
    final String taskName = "Task A";
    final String bpmnProcessId = "testProcess";
    final String taskId = "taskA";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .name(workflowName)
            .startEvent("start")
            .serviceTask(taskId)
            .name(taskName)
            .zeebeJobType(tasklistProperties.getImporter().getJobType())
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .having()
            .deployWorkflow(workflow, "testWorkflow.bpmn")
            .waitUntil()
            .workflowIsDeployed()
            .and()
            .startWorkflowInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(taskId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertEquals(taskName, response.get("$.data.tasks[0].name"));
    assertEquals(workflowName, response.get("$.data.tasks[0].workflowName"));
  }

  @Test
  public void shouldFallbackToTaskIdAndBpmnProcessId() throws IOException {
    // having
    final String bpmnProcessId = "testProcess";
    final String taskId = "taskA";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(bpmnProcessId)
            // .name("Test process name")
            .startEvent("start")
            .serviceTask(taskId)
            // .name("Task A")
            .zeebeJobType(tasklistProperties.getImporter().getJobType())
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .having()
            .deployWorkflow(workflow, "testWorkflow.bpmn")
            .and()
            .startWorkflowInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(taskId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertEquals("taskA", response.get("$.data.tasks[0].name"));
    assertEquals("testProcess", response.get("$.data.tasks[0].workflowName"));
  }

  @Test
  public void shouldReturnOneTask() throws IOException {
    final GraphQLResponse response =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
            .when()
            .getAllTasks();

    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse =
        tester.when().getTaskByQuery(String.format(GET_TASK_QUERY_PATTERN, taskId));

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(BPMN_PROCESS_ID, taskResponse.get("$.data.task.workflowName"));
    assertNotNull(taskResponse.get("$.data.task.creationTime"));
    assertNull(taskResponse.get("$.data.task.completionTime"));
    assertEquals(TaskState.CREATED.name(), taskResponse.get("$.data.task.taskState"));
    assertNull(taskResponse.get("$.data.task.assignee"));
    assertEquals("0", taskResponse.get("$.data.task.variables.length()"));
  }

  @Test
  public void shouldNotReturnTaskWithWrongId() throws IOException {
    final GraphQLResponse taskResponse =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
            .when()
            .getTaskByQuery(String.format(GET_TASK_QUERY_PATTERN, "wrongTaskId"));
    // then
    assertNull(taskResponse.get("$.data"));
    assertEquals("1", taskResponse.get("$.errors.length()"));
    assertEquals("Task with id wrongTaskId was not found", taskResponse.get("$.errors[0].message"));
  }
}
