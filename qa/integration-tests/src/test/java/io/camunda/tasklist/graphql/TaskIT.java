/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.util.ElasticsearchChecks.TestCheck;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class TaskIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String TASK_RESULT_PATTERN = "{id name assignee}";
  public static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCanceledCheck;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private ObjectMapper objectMapper;

  private final UserDTO joe = buildAllAccessUserWith("joe", "Joe Doe");
  private final UserDTO jane = buildAllAccessUserWith("jane", "Jane Doe");
  private final UserDTO demo = buildAllAccessUserWith(DEFAULT_USER_ID, DEFAULT_DISPLAY_NAME);

  private static UserDTO buildAllAccessUserWith(final String userId, final String displayName) {
    return new UserDTO()
        .setUserId(userId)
        .setDisplayName(displayName)
        .setPermissions(List.of(Permission.WRITE));
  }

  @Before
  public void before() {
    super.before();
    taskMutationResolver.setZeebeClient(super.getClient());
  }

  @Test
  public void shouldReturnAllTasks() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final GraphQLResponse response =
        tester
            .having()
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 3)
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

      // process does not contain task name and process name
      assertEquals(flowNodeBpmnId, response.get(taskJsonPath + ".name"));
      assertEquals(bpmnProcessId, response.get(taskJsonPath + ".processName"));

      assertNotNull(response.get(taskJsonPath + ".creationTime"));
      assertNull(response.get(taskJsonPath + ".completionTime"));
      assertEquals(TaskState.CREATED.name(), response.get(taskJsonPath + ".taskState"));
      assertNull(response.get(taskJsonPath + ".assignee"));
      assertNotNull(response.get(taskJsonPath + ".processDefinitionId"));
      assertNotNull(response.get(taskJsonPath + ".processInstanceId"));
      assertEquals("0", response.get(taskJsonPath + ".variables.length()"));
    }
    assertSorting(response);
    assertIsFirst(response, true);
  }

  @Test
  public void shouldReturnPages() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    tester
        .having()
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 10)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 10);

    // when querying page 1
    final ObjectNode queryPage1 = objectMapper.createObjectNode();
    queryPage1.putObject("query").put("pageSize", 3);
    GraphQLResponse response = tester.getTasksByQueryAsVariable(queryPage1);

    // then
    assertTasksPage(response, 3, null, null, true);
    List<String> sortValues = response.getList("$.data.tasks[2].sortValues", String.class);

    // when querying page 2
    final ObjectNode queryPage2 = objectMapper.createObjectNode();
    queryPage2
        .putObject("query")
        .put("pageSize", 4)
        .putArray("searchAfter")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    response = tester.getTasksByQueryAsVariable(queryPage2);

    // then
    assertTasksPage(response, 4, sortValues, null, false);
    sortValues = response.getList("$.data.tasks[3].sortValues", String.class);

    // when querying page 3
    final ObjectNode queryPage3 = objectMapper.createObjectNode();
    queryPage3
        .putObject("query")
        .put("pageSize", 4)
        .putArray("searchAfter")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    response = tester.getTasksByQueryAsVariable(queryPage3);

    // then
    assertTasksPage(response, 3, sortValues, null, false);
    sortValues = response.getList("$.data.tasks[0].sortValues", String.class);

    // when querying with searchBefore
    final ObjectNode queryPage4 = objectMapper.createObjectNode();
    queryPage4
        .putObject("query")
        .put("pageSize", 5)
        .putArray("searchBefore")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    response = tester.getTasksByQueryAsVariable(queryPage4);

    // then
    assertTasksPage(response, 5, null, sortValues, false);
  }

  @Test
  public void shouldReturnPagesWithAfterBeforeOrEqual() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    tester
        .having()
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 10)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 10);

    // when querying page 1
    final ObjectNode queryPage1 = objectMapper.createObjectNode();
    queryPage1.putObject("query").put("pageSize", 3);
    final GraphQLResponse responsePage1 = tester.getTasksByQueryAsVariable(queryPage1);

    // then
    assertEquals("3", responsePage1.get("$.data.tasks.length()"));
    List<String> sortValues = responsePage1.getList("$.data.tasks[0].sortValues", String.class);

    // when querying page 1 once again with searchAfterOrEqual
    final ObjectNode query2Page1 = objectMapper.createObjectNode();
    query2Page1
        .putObject("query")
        .put("pageSize", 3)
        .putArray("searchAfterOrEqual")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    final GraphQLResponse response2Page1 = tester.getTasksByQueryAsVariable(query2Page1);

    // then
    assertEquals("3", response2Page1.get("$.data.tasks.length()"));
    assertIsFirst(response2Page1, true);
    sortValues = response2Page1.getList("$.data.tasks[2].sortValues", String.class);

    // when querying page 2
    final ObjectNode query1Page2 = objectMapper.createObjectNode();
    query1Page2
        .putObject("query")
        .put("pageSize", 4)
        .putArray("searchAfter")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    final GraphQLResponse response1Page2 = tester.getTasksByQueryAsVariable(query1Page2);

    // then
    assertEquals("4", response1Page2.get("$.data.tasks.length()"));
    sortValues = response1Page2.getList("$.data.tasks[0].sortValues", String.class);

    // when querying page 2 once again with searchAfterOrEqual
    final ObjectNode query2Page2 = objectMapper.createObjectNode();
    query2Page2
        .putObject("query")
        .put("pageSize", 4)
        .putArray("searchAfterOrEqual")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    final GraphQLResponse response2Page2 = tester.getTasksByQueryAsVariable(query2Page2);

    // then
    assertEquals("4", response2Page2.get("$.data.tasks.length()"));
    for (int i = 0; i < 4; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertEquals(
          response1Page2.get(taskJsonPath + ".id"), response2Page2.get(taskJsonPath + ".id"));
    }
    assertIsFirst(response2Page2, false);
    sortValues = response1Page2.getList("$.data.tasks[3].sortValues", String.class);

    // when querying page 2 once again with searchBeforeOrEqual
    final ObjectNode query3Page2 = objectMapper.createObjectNode();
    query3Page2
        .putObject("query")
        .put("pageSize", 4)
        .putArray("searchBeforeOrEqual")
        .add(sortValues.get(0))
        .add(sortValues.get(1));
    final GraphQLResponse response3Page2 = tester.getTasksByQueryAsVariable(query3Page2);

    // then
    assertEquals("4", response3Page2.get("$.data.tasks.length()"));
    for (int i = 0; i < 4; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertEquals(
          response1Page2.get(taskJsonPath + ".id"), response3Page2.get(taskJsonPath + ".id"));
    }
    assertIsFirst(response3Page2, false);
  }

  private void assertIsFirst(final GraphQLResponse response, final boolean hasFirst) {
    for (int i = 0; i < Integer.valueOf(response.get("$.data.length()")); i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      if (i == 0 && hasFirst) {
        assertThat(response.get(taskJsonPath + ".isFirst")).isEqualTo("true");
      } else {
        assertThat(response.get(taskJsonPath + ".isFirst")).isEqualTo("false");
      }
    }
  }

  private void assertIsFirst(final List<TaskDTO> tasks, final boolean hasFirst) {
    for (int i = 0; i < tasks.size(); i++) {
      if (i == 0 && hasFirst) {
        assertTrue(tasks.get(i).getIsFirst());
      } else {
        assertFalse(tasks.get(i).getIsFirst());
      }
    }
  }

  private void assertTasksPage(
      final GraphQLResponse response,
      int pageSize,
      List<String> searchAfter,
      List<String> searchBefore,
      boolean hasFirst) {
    assertTrue(response.isOk());
    assertEquals(String.valueOf(pageSize), response.get("$.data.tasks.length()"));
    for (int i = 0; i < pageSize; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertNotNull(response.get(taskJsonPath + ".id"));
      assertNotNull(response.get(taskJsonPath + ".creationTime"));
      assertEquals(TaskState.CREATED.name(), response.get(taskJsonPath + ".taskState"));

      final List<String> sortValues = response.getList(taskJsonPath + ".sortValues", String.class);
      assertNotNull(sortValues);
      assertThat(sortValues).hasSize(2);
      // default sorting is descendant for creation date
      if (searchAfter != null) {
        final Long sortValue1 = Long.valueOf(sortValues.get(0));
        assertThat(sortValue1).isLessThanOrEqualTo(Long.valueOf(searchAfter.get(0)));
      }
      if (searchBefore != null) {
        final Long sortValue1 = Long.valueOf(sortValues.get(0));
        assertThat(sortValue1).isGreaterThanOrEqualTo(Long.valueOf(searchBefore.get(0)));
      }
    }
    assertIsFirst(response, hasFirst);
    assertSorting(response);
  }

  @Test
  public void shouldNotReturnTasksForCancelledProcessInstances() throws IOException {
    final String bpmnProcessId = "testProcess", flowNodeBpmnId = "taskA";

    final GraphQLResponse response =
        tester
            .having()
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 5)
            .waitUntil()
            .tasksAreCreated(flowNodeBpmnId, 5)
            .when()
            .cancelProcessInstance()
            .and()
            .waitUntil()
            .processInstanceIsCanceled()
            .then()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertThat(response.get("$.data.tasks.length()")).isEqualTo("4");
  }

  @Test
  public void shouldNotReturnCanceledTasksWithBoundaryEvents() throws IOException {
    final String processId = "boundaryTimer", flowNowBPMNId = "noTimeToComplete";
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(processId + ".bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(processId)
            .when()
            .waitUntil()
            .taskIsCanceled(flowNowBPMNId)
            .then()
            .getAllTasks();
    assertThat(response.get("$.data.tasks.length()")).isEqualTo("0");
  }

  @Test
  public void shouldNotReturnCanceledTasksInInterruptingBoundaryEvent() throws IOException {
    final String processId = "interruptingBoundaryEvent",
        flowNodeBPMNId = "task1",
        flowNodeBPMNId2 = "task2";
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(processId + "_v_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(processId)
            .when()
            .waitUntil()
            .taskIsCanceled(flowNodeBPMNId)
            .waitUntil()
            .taskIsCreated(flowNodeBPMNId2)
            .then()
            .getAllTasks();
    assertThat(response.get("$.data.tasks.length()")).isEqualTo("1");
    assertThat(response.get("$.data.tasks[0].name")).isEqualTo("Task 2");
  }

  @Test
  public void shouldNotReturnCanceledTasksInEventSubprocess() throws IOException {
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess("eventSubProcessWithTimers.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance("eventSubprocessProcess")
            .waitUntil()
            .taskIsCreated("parentProcessTask")
            .claimAndCompleteHumanTask("parentProcessTask")
            .waitUntil()
            .taskIsCreated("subprocessTask")
            .waitUntil()
            .taskIsCreated("taskInSubprocess")
            .and()
            .taskIsCanceled("subprocessTask")
            .and()
            .taskIsCanceled("taskInSubprocess")
            .then()
            .getAllTasks();

    assertThat(response.get("$.data.tasks.length()")).isEqualTo("1");
    assertThat(response.get("$.data.tasks[0].name")).isEqualTo("Parent process task");
    assertThat(response.get("$.data.tasks[0].taskState")).isEqualTo("COMPLETED");
  }

  private void assertSorting(GraphQLResponse response) {
    final List<TaskDTO> tasks = Arrays.asList(response.get("$.data.tasks", TaskDTO[].class));
    final Comparator<TaskDTO> comparator = comparing(TaskDTO::getCreationTime).reversed();
    assertThat(tasks).isSortedAccordingTo(comparator);
  }

  @Test
  public void shouldReturnCompletedTask() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final GraphQLResponse response =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
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
    assertEquals(flowNodeBpmnId, response.get("$.data.tasks[0].taskDefinitionId"));
    assertEquals(bpmnProcessId, response.get("$.data.tasks[0].processName"));
    assertNotNull(response.get("$.data.tasks[0].creationTime"));
    assertNotNull(response.get("$.data.tasks[0].completionTime"));
    assertNotNull(response.get("$.data.tasks[0].processInstanceId"));
    assertNotNull(response.get("$.data.tasks[0].processDefinitionId"));
    assertEquals(TaskState.COMPLETED.name(), response.get("$.data.tasks[0].taskState"));
    assertNotNull(response.get("$.data.tasks[0].assignee"));
    assertEquals("0", response.get("$.data.tasks[0].variables.length()"));
    assertIsFirst(response, true);
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
    assertIsFirst(createdTasks, true);
  }

  @Test
  public void shouldReturnAllCompletedTasks() throws IOException {
    final int completedTasksCount = 5;
    final List<TaskDTO> completedTasks =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, completedTasksCount)
            .when()
            .getCompletedTasks();
    // then
    assertThat(completedTasks).hasSize(completedTasksCount);
    assertThat(completedTasks.stream()).allMatch(t -> TaskState.COMPLETED.equals(t.getTaskState()));
    assertThat(completedTasks)
        .isSortedAccordingTo(comparing(TaskDTO::getCompletionTime).reversed());
    assertIsFirst(completedTasks, true);
  }

  @Test
  public void shouldReturnUnclaimedTasks() {
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
            .getTasksByQuery("{tasks(query: {assigned: false}) {id creationTime}}");
    assertEquals(2, tasksAfterOneClaimed.size());
    assertThat(tasksAfterOneClaimed)
        .isSortedAccordingTo(comparing(TaskDTO::getCreationTime).reversed());
  }

  @Test
  public void shouldReturnTaskBasedOnTaskDefinitionId() throws IOException {
    // when #1
    final List<TaskDTO> tasks =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, "taskA", 3, 0)
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, "taskB", 3, 0)
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, "taskC", 3, 0)
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, "taskD", 3, 0)
            .then()
            .getCreatedTasks();
    assertEquals(12, tasks.size());

    final List<TaskDTO> tasksByQuery =
        tester.getTasksByQuery("{tasks(query: {taskDefinitionId: \"taskB\"}) {id}}");

    assertEquals(3, tasksByQuery.size());
  }

  @Test
  public void shouldReturnTaskBasedOnProcessDefinitionId() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final GraphQLResponse response =
        tester
            .having()
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 3)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks();

    final String responseProcessDefinitionId = response.get("$.data.tasks[0].processDefinitionId");

    assertTrue(response.isOk());
    assertNotNull(responseProcessDefinitionId);

    final List<TaskDTO> tasks =
        tester.getTasksByQuery(
            "{tasks(query: {processDefinitionId: \""
                + responseProcessDefinitionId
                + "\"}) {processDefinitionId}}");

    assertEquals(3, tasks.size());
    for (TaskDTO taskDto : tasks) {
      assertEquals(taskDto.getProcessDefinitionId(), responseProcessDefinitionId);
    }
  }

  @Test
  public void shouldReturnTaskBasedOnProcessInstanceId() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final GraphQLResponse response =
        tester
            .having()
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 3)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks();

    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 3; i++) {
      final String responseProcessInstanceId =
          response.get("$.data.tasks[" + i + "].processInstanceId");
      assertNotNull(responseProcessInstanceId);

      final List<TaskDTO> tasks =
          tester.getTasksByQuery(
              "{tasks(query: {processInstanceId: \""
                  + responseProcessInstanceId
                  + "\"}) {processInstanceId}}");

      assertEquals(1, tasks.size());

      for (TaskDTO taskDto : tasks) {
        assertEquals(taskDto.getProcessInstanceId(), responseProcessInstanceId);
      }
    }
  }

  @Test
  public void shouldReturnClaimedByUser() throws IOException {
    List<TaskDTO> tasks =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, 1)
            .when()
            .getTasksByQuery(
                "{tasks(query: {assignee: \"" + DEFAULT_USER_ID + "\", state: CREATED}) {id}}");
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
            .getTasksByQuery(
                "{tasks(query: { assignee: \"" + DEFAULT_USER_ID + "\", state: CREATED}) {id}}");
    assertEquals(2, tasks.size());
  }

  @Test
  public void shouldReturnTasksClaimedByDifferentUsers() throws IOException {
    // given users joe, jane and demo
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
        tester.getTasksByQuery(
            "{tasks(query: {assignee: \"" + DEFAULT_USER_ID + "\", state: CREATED}) {id}}");
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
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(bpmnProcessId)
              .startEvent()
              .userTask(flowNodeBpmnId)
              .endEvent()
              .done();
      tester
          .deployProcess(process, process + ".bpmn")
          .waitUntil()
          .processIsDeployed()
          .and()
          .startProcessInstance(bpmnProcessId, "{\"flowNodeBpmnId\": \"" + flowNodeBpmnId + "\"}")
          .waitUntil()
          .taskIsCreated(flowNodeBpmnId);
      if (i % 2 == 0) {
        tester.claimAndCompleteHumanTask(flowNodeBpmnId);
      }
    }
  }

  @Test
  public void shouldReturnProcessAndTaskName() throws IOException {
    // having
    final String processName = "Test process name";
    final String taskName = "Task A";
    final String bpmnProcessId = "testProcess";
    final String taskId = "taskA";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .name(processName)
            .startEvent("start")
            .userTask(taskId)
            .name(taskName)
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(process, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(taskId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertEquals(taskName, response.get("$.data.tasks[0].name"));
    assertEquals(processName, response.get("$.data.tasks[0].processName"));
  }

  @Test
  public void shouldFallbackToTaskIdAndBpmnProcessId() throws IOException {
    // having
    final String bpmnProcessId = "testProcess";
    final String taskId = "taskA";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent("start")
            .userTask(taskId)
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(process, "testProcess.bpmn")
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(taskId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertEquals("taskA", response.get("$.data.tasks[0].name"));
    assertEquals("testProcess", response.get("$.data.tasks[0].processName"));
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
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(BPMN_PROCESS_ID, taskResponse.get("$.data.task.processName"));
    assertNotNull(taskResponse.get("$.data.task.creationTime"));
    assertNull(taskResponse.get("$.data.task.completionTime"));
    assertEquals(TaskState.CREATED.name(), taskResponse.get("$.data.task.taskState"));
    assertNull(taskResponse.get("$.data.task.assignee"));
    assertNotNull(taskResponse.get("$.data.task.processDefinitionId"));
    assertNotNull(taskResponse.get("$.data.task.processInstanceId"));
    assertEquals("0", taskResponse.get("$.data.task.variables.length()"));
  }

  @Test
  public void shouldNotReturnTaskWithWrongId() throws IOException {
    final GraphQLResponse taskResponse =
        tester
            .having()
            .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
            .when()
            .getTaskById("wrongTaskId");
    // then
    assertNull(taskResponse.get("$.data"));
    assertEquals("1", taskResponse.get("$.errors.length()"));
    assertEquals("Task with id wrongTaskId was not found", taskResponse.get("$.errors[0].message"));
  }
}
