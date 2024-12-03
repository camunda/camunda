/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.util.TestCheck.TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.camunda.tasklist.util.TestCheck.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.assertions.CustomAssertions;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class TaskIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String TASK_RESULT_PATTERN = "{id name assignee}";
  public static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  private static final SimpleDateFormat SIMPLE_DATE_FORMAT =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

  private MockMvcHelper mockMvcHelper;

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCanceledCheck;

  @Autowired private WebApplicationContext context;

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

  private BpmnModelInstance getModelForDueAndFollowUpDates(
      final Date dueDate, final Date followUpDate) {
    final String dueDateString = SIMPLE_DATE_FORMAT.format(dueDate);
    final String followUpDateString = SIMPLE_DATE_FORMAT.format(followUpDate);

    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent("start")
            .userTask(
                ELEMENT_ID,
                task -> {
                  task.zeebeDueDate(dueDateString);
                  task.zeebeFollowUpDate(followUpDateString);
                })
            .endEvent()
            .done();
    return model;
  }

  private void assertSorting(final GraphQLResponse response) {
    final List<TaskDTO> tasks = Arrays.asList(response.get("$.data.tasks", TaskDTO[].class));
    final Comparator<TaskDTO> comparator = comparing(TaskDTO::getCreationTime).reversed();
    assertThat(tasks).isSortedAccordingTo(comparator);
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
      final int pageSize,
      final List<String> searchAfter,
      final List<String> searchBefore,
      final boolean hasFirst) {
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

  @Override
  @BeforeEach
  public void before() {
    super.before();
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Nested
  class TaskRetrievalByCriteriaTests {
    @Nested
    class RetrieveByIdAndDefinition {

      @Test
      public void shouldReturnTaskBasedOnProcessInstanceAndDefinition() throws IOException {
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

        final String responseProcessDefinitionId =
            response.get("$.data.tasks[0].processDefinitionId");

        final String responseProcessInstanceId = response.get("$.data.tasks[0].processInstanceId");

        assertTrue(response.isOk());
        assertNotNull(responseProcessDefinitionId);
        assertNotNull(responseProcessInstanceId);

        final List<TaskDTO> tasksByProcessDefinition =
            tester.getTasksByQuery(
                "{tasks(query: {processDefinitionId: \""
                    + responseProcessDefinitionId
                    + "\"}) {processDefinitionId}}");

        assertEquals(3, tasksByProcessDefinition.size());
        for (final TaskDTO taskDto : tasksByProcessDefinition) {
          assertEquals(taskDto.getProcessDefinitionId(), responseProcessDefinitionId);
        }

        final List<TaskDTO> tasksByProcessInstance =
            tester.getTasksByQuery(
                "{tasks(query: {processInstanceId: \""
                    + responseProcessInstanceId
                    + "\"}) {processInstanceId}}");

        assertEquals(1, tasksByProcessInstance.size());

        for (final TaskDTO taskDto : tasksByProcessInstance) {
          assertEquals(taskDto.getProcessInstanceId(), responseProcessInstanceId);
        }
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
    }

    @Nested
    class RetrieveByDateFilters {
      @Test
      public void shouldNotReturnWithFollowUpAndDueDatesFilter() {
        final Calendar c = Calendar.getInstance();
        c.setTime(new Date());

        c.add(Calendar.DATE, 1);
        final Date dueDate = c.getTime();

        c.add(Calendar.DATE, 1);
        final Date followUpDate = c.getTime();

        c.add(Calendar.DATE, 2);
        final Date followUpDateFrom = c.getTime();
        c.add(Calendar.DATE, 2);
        final Date dueDateFrom = c.getTime();
        c.add(Calendar.DATE, 1);
        final Date followUpDateTo = c.getTime();
        c.add(Calendar.DATE, -1);
        final Date dueDateTo = c.getTime();

        final BpmnModelInstance model = getModelForDueAndFollowUpDates(dueDate, followUpDate);

        tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID);

        final GraphQLResponse responseFollowUpDate =
            tester.getGraphTasksByQuery(
                "{tasks(query:{followUpDate:{"
                    + "from : \""
                    + SIMPLE_DATE_FORMAT.format(followUpDateFrom)
                    + "\","
                    + "to:  \""
                    + SIMPLE_DATE_FORMAT.format(followUpDateTo)
                    + "\""
                    + "}"
                    + "}){id followUpDate}}");

        final GraphQLResponse responseDueDate =
            tester.getGraphTasksByQuery(
                "{tasks(query:{dueDate:{"
                    + "from : \""
                    + SIMPLE_DATE_FORMAT.format(dueDateFrom)
                    + "\","
                    + "to:  \""
                    + SIMPLE_DATE_FORMAT.format(dueDateTo)
                    + "\""
                    + "}"
                    + "}){id dueDate}}");

        assertTrue(responseFollowUpDate.isOk());
        assertEquals("0", responseFollowUpDate.get("$.data.tasks.length()"));
        assertTrue(responseDueDate.isOk());
        assertEquals("0", responseFollowUpDate.get("$.data.tasks.length()"));
      }

      @Test
      public void shouldReturnWithFollowUpAndDueDatesFilter() {
        final Calendar c = Calendar.getInstance();
        c.setTime(new Date());

        c.add(Calendar.DATE, 2);
        final Date followUpDateFrom = c.getTime();
        final Date dueDateFrom = c.getTime();
        c.add(Calendar.DATE, 1);
        final Date dueDate = c.getTime();
        c.add(Calendar.DATE, 1);
        final Date followUpDate = c.getTime();
        c.add(Calendar.DATE, 1);
        final Date followUpDateTo = c.getTime();

        final BpmnModelInstance model = getModelForDueAndFollowUpDates(dueDate, followUpDate);

        tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID);

        final GraphQLResponse responseFollowUpDate =
            tester.getGraphTasksByQuery(
                "{tasks(query:{followUpDate:{"
                    + "from : \""
                    + SIMPLE_DATE_FORMAT.format(followUpDateFrom)
                    + "\","
                    + "to:  \""
                    + SIMPLE_DATE_FORMAT.format(followUpDateTo)
                    + "\""
                    + "}"
                    + "}){id followUpDate}}");

        assertTrue(responseFollowUpDate.isOk());
        assertEquals("1", responseFollowUpDate.get("$.data.tasks.length()"));
        assertEquals(
            DateUtil.toOffsetDateTime(SIMPLE_DATE_FORMAT.format(followUpDate)),
            DateUtil.toOffsetDateTime(responseFollowUpDate.get("$.data.tasks[0].followUpDate")));

        c.add(Calendar.DATE, 1);
        final Date dueDateTo = c.getTime();

        final GraphQLResponse responseDueDate =
            tester.getGraphTasksByQuery(
                "{tasks(query:{dueDate:{"
                    + "from : \""
                    + SIMPLE_DATE_FORMAT.format(dueDateFrom)
                    + "\","
                    + "to:  \""
                    + SIMPLE_DATE_FORMAT.format(dueDateTo)
                    + "\""
                    + "}"
                    + "}){id dueDate}}");

        assertTrue(responseDueDate.isOk());
        assertEquals("1", responseDueDate.get("$.data.tasks.length()"));
        assertEquals(
            DateUtil.toOffsetDateTime(SIMPLE_DATE_FORMAT.format(dueDate)),
            DateUtil.toOffsetDateTime(responseDueDate.get("$.data.tasks[0].dueDate")));
      }
    }

    @Nested
    class RetrieveByStateAndStatus {
      @Test
      public void shouldReturnAllOpenAndCompletedTasks() throws IOException {
        final List<TaskDTO> createdTasks =
            tester
                .having()
                .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, 3)
                .and()
                .when()
                .getCreatedTasks();

        final List<TaskDTO> completedTasks =
            tester
                .having()
                .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 2, 5)
                .when()
                .getCompletedTasks();

        // then
        assertEquals(2, createdTasks.size());
        createdTasks.forEach(t -> assertEquals(TaskState.CREATED, t.getTaskState()));
        assertIsFirst(createdTasks, true);

        // then
        assertThat(completedTasks).hasSize(completedTasks.size());
        assertThat(completedTasks.stream())
            .allMatch(t -> TaskState.COMPLETED.equals(t.getTaskState()));
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
                    "{tasks(query: { assignee: \""
                        + DEFAULT_USER_ID
                        + "\", state: CREATED}) {id}}");
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
        List<TaskDTO> unclaimedTasks =
            tester.getTasksByQuery("{tasks(query: {assigned: false}) {id}}");
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
    }

    @Nested
    class RetrieveByNameAndVariables {
      private List<TaskDTO> startProcessWithCandidateUserAndSearchBy(
          final String candidateUsersInput, final String candidateUserQuery) {

        final BpmnModelInstance model =
            Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
                .startEvent("start")
                .userTask(
                    ELEMENT_ID,
                    task -> {
                      task.zeebeCandidateUsers(candidateUsersInput);
                    })
                .endEvent()
                .done();
        return tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .getTasksByQuery(
                "{tasks(query: {candidateUser: \""
                    + candidateUserQuery
                    + "\"}) {id candidateUsers}}");
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
      public void shouldReturnTaskWithCandidateUser() {
        final String candidateUser1 = "user1";
        final String candidateUser2 = "user2";
        final String candidateUsers = candidateUser1 + ", " + candidateUser2;

        final List<TaskDTO> tasks =
            startProcessWithCandidateUserAndSearchBy(candidateUsers, candidateUser2);
        assertEquals(1, tasks.size());
        assertTrue(Arrays.asList(tasks.get(0).getCandidateUsers()).contains(candidateUser2));
      }

      @Test
      public void shouldNotReturnTasksWithCandidateUser() {
        final String candidateUsers = "random1,random2";
        final List<TaskDTO> tasks =
            startProcessWithCandidateUserAndSearchBy(candidateUsers, "random3");
        assertEquals(0, tasks.size());
      }
    }

    @Nested
    class NotRetrieveByCancellationStates {
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
                .taskIsCanceled(flowNodeBpmnId)
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
    }
  }

  @Nested
  class SearchTasksAndPaginationTests {
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

    @Test
    public void shouldReturnAllCompletedTask() throws IOException {
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
      assertEquals(
          "task with id wrongTaskId was not found", taskResponse.get("$.errors[0].message"));
    }
  }

  @Nested
  class StartProcessWithDatesTests {
    @Test
    public void startProcessWithDueAndFollowUpDates() throws IOException {
      final Calendar c = Calendar.getInstance();
      c.setTime(new Date());

      c.add(Calendar.DATE, 3);
      final Date dueDate = c.getTime();
      c.add(Calendar.DATE, 1);
      final Date followUpDate = c.getTime();

      final BpmnModelInstance model = getModelForDueAndFollowUpDates(dueDate, followUpDate);

      final GraphQLResponse response =
          tester
              .having()
              .deployProcess(model, "testProcess.bpmn")
              .waitUntil()
              .processIsDeployed()
              .and()
              .startProcessInstance(BPMN_PROCESS_ID)
              .waitUntil()
              .taskIsCreated(ELEMENT_ID)
              .getAllTasks();

      assertEquals("1", response.get("$.data.tasks.length()"));
      assertEquals(
          DateUtil.toOffsetDateTime(SIMPLE_DATE_FORMAT.format(dueDate)),
          DateUtil.toOffsetDateTime(response.get("$.data.tasks[0].dueDate")));
      assertEquals(
          DateUtil.toOffsetDateTime(SIMPLE_DATE_FORMAT.format(followUpDate)),
          DateUtil.toOffsetDateTime(response.get("$.data.tasks[0].followUpDate")));
    }

    @Test
    public void startProcessWithDueAndFollowUpDatesAsExpressions() throws IOException {
      final Calendar c = Calendar.getInstance();
      c.setTime(new Date());
      c.add(Calendar.DATE, 3);
      final String dueDate = SIMPLE_DATE_FORMAT.format(c.getTime());

      c.add(Calendar.DATE, 1);
      final String followUpDate = SIMPLE_DATE_FORMAT.format(c.getTime());

      final BpmnModelInstance model =
          Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
              .startEvent("start")
              .userTask(
                  ELEMENT_ID,
                  task -> {
                    task.zeebeDueDateExpression("=dueDate");
                    task.zeebeFollowUpDateExpression("=followUpDate");
                  })
              .endEvent()
              .done();

      final String payload =
          "{\"dueDate\": \"" + dueDate + "\", \"followUpDate\" : \"" + followUpDate + "\"}";

      final GraphQLResponse response =
          tester
              .having()
              .deployProcess(model, "testProcess.bpmn")
              .waitUntil()
              .processIsDeployed()
              .and()
              .startProcessInstance(BPMN_PROCESS_ID, payload)
              .waitUntil()
              .taskIsCreated(ELEMENT_ID)
              .getAllTasks();

      assertTrue(response.isOk());

      assertEquals("1", response.get("$.data.tasks.length()"));
      assertEquals(
          DateUtil.toOffsetDateTime(dueDate),
          DateUtil.toOffsetDateTime(response.get("$.data.tasks[0].dueDate")));
      assertEquals(
          DateUtil.toOffsetDateTime(followUpDate),
          DateUtil.toOffsetDateTime(response.get("$.data.tasks[0].followUpDate")));
    }
  }

  @Nested
  class TaskStates {
    @Test
    public void shouldFailTaskJobAndReturnTaskAsCreated() throws IOException {
      final int numberOfTasks = 2;

      final var searchQuery = new TaskQueryDTO().setState(TaskState.CREATED);

      tester
          .having()
          .createFailedTasks(BPMN_PROCESS_ID, ELEMENT_ID, numberOfTasks, 1)
          .waitUntil()
          .taskIsFailed(ELEMENT_ID);

      final var result =
          mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

      CustomAssertions.assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(numberOfTasks);
    }

    @Test
    public void shouldFailTaskJobAndReturnTaskAsFailed() throws IOException {
      final int numberOfTasks = 2;

      final var searchQuery = new TaskQueryDTO().setState(TaskState.FAILED);
      tester
          .having()
          .createFailedTasks(BPMN_PROCESS_ID, ELEMENT_ID, numberOfTasks, 0)
          .waitUntil()
          .taskIsFailed(ELEMENT_ID);

      final var result =
          mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

      CustomAssertions.assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(numberOfTasks);
    }
  }

  @Nested
  class ZeebeApiTests {
    @Test
    public void shouldAssignUserTask() {
      final String user = "demo";
      final String taskId =
          tester
              .createAndDeploySimpleProcess(
                  BPMN_PROCESS_ID, ELEMENT_ID, AbstractUserTaskBuilder::zeebeUserTask)
              .processIsDeployed()
              .then()
              .startProcessInstance(BPMN_PROCESS_ID)
              .then()
              .taskIsCreated(ELEMENT_ID)
              .getTaskId();

      zeebeClient.newUserTaskAssignCommand(Long.valueOf(taskId)).assignee(user).send().join();

      tester.waitUntil().taskIsAssigned(taskId);

      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId));

      CustomAssertions.assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, TaskResponse.class)
          .satisfies(
              task -> {
                assertThat(task.getId()).isEqualTo(taskId);
                assertThat(task.getName()).isEqualTo(ELEMENT_ID);
                assertThat(task.getProcessName()).isEqualTo(BPMN_PROCESS_ID);
                assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
                assertThat(task.getAssignee()).isEqualTo(user);
              });
    }

    @Test
    public void shouldUpdateUserTask() {
      final String taskId =
          tester
              .createAndDeploySimpleProcess(
                  BPMN_PROCESS_ID, ELEMENT_ID, AbstractUserTaskBuilder::zeebeUserTask)
              .processIsDeployed()
              .then()
              .startProcessInstance(BPMN_PROCESS_ID)
              .then()
              .taskIsCreated(ELEMENT_ID)
              .getTaskId();
      final List<String> candidateGroups = new ArrayList<>();
      candidateGroups.add("candidateGroupA");
      candidateGroups.add("candidateGroupB");

      final List<String> candidateUsers = new ArrayList<>();
      candidateUsers.add("candidateUserA");
      candidateUsers.add("candidateUserB");

      final String dueDate = "2024-03-08T18:41:31+00:00";
      final String followUpDate = "2024-03-08T18:41:31+00:00";

      zeebeClient
          .newUserTaskUpdateCommand(Long.valueOf(taskId))
          .candidateUsers(candidateUsers)
          .action("action")
          .candidateGroups(candidateGroups)
          .dueDate(dueDate)
          .followUpDate(followUpDate)
          .send()
          .join();

      tester.waitUntil().taskHasCandidateUsers(ELEMENT_ID);

      final var result =
          mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId));

      CustomAssertions.assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, TaskResponse.class)
          .satisfies(
              task -> {
                assertThat(task.getId()).isEqualTo(taskId);
                assertThat(task.getName()).isEqualTo(ELEMENT_ID);
                assertThat(task.getProcessName()).isEqualTo(BPMN_PROCESS_ID);
                assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
                assertThat(task.getCandidateGroups()).containsAll(candidateGroups);
                assertThat(task.getCandidateUsers()).containsAll(candidateUsers);
                assertThat(task.getDueDate()).isEqualTo(dueDate);
                assertThat(task.getFollowUpDate()).isEqualTo(followUpDate);
              });
    }

    @Test
    public void shouldCompleteUserTaskWithVariables() {
      final String taskId =
          tester
              .createAndDeploySimpleProcess(
                  BPMN_PROCESS_ID, ELEMENT_ID, AbstractUserTaskBuilder::zeebeUserTask)
              .processIsDeployed()
              .then()
              .startProcessInstance(BPMN_PROCESS_ID)
              .then()
              .taskIsCreated(ELEMENT_ID)
              .getTaskId();
      final Map<String, Object> variables = new HashMap<>();
      variables.put("varA", "value Var A");
      variables.put("varB", 123);
      variables.put("varC", 123L);

      zeebeClient
          .newUserTaskCompleteCommand(Long.parseLong(taskId))
          .variables(variables)
          .send()
          .join();

      tester.waitUntil().taskIsCompleted(ELEMENT_ID);

      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

      CustomAssertions.assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .extracting("name", "value")
          .containsExactlyInAnyOrder(
              tuple("varA", "\"value Var A\""), tuple("varB", "123"), tuple("varC", "123"));
    }
  }
}
