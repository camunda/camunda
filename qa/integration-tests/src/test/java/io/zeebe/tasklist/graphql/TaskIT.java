/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.graphql;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.zeebe.ImportValueType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String GET_TASK_QUERY_PATTERN = "{task(id: \"%s\"){id name workflowName creationTime completionTime assignee {username} variables {name} taskState}}";
  @Autowired
  private GraphQLTestTemplate graphQLTestTemplate;

  @Autowired
  @Qualifier("taskIsCreatedCheck")
  private Predicate<Object[]> taskIsCreatedCheck;

  @Test
  public void shouldReturnAllTasks() throws IOException {
    //having
    final String bpmnProcessId = "testProcess";
    final String elementId = "taskA";
    tester.createAndDeploySimpleWorkflow(bpmnProcessId, elementId)
        .waitUntil()
        .workflowIsDeployed()
        .and()
        .startWorkflowInstance(bpmnProcessId)
        .startWorkflowInstance(bpmnProcessId)
        .startWorkflowInstance(bpmnProcessId)
        .waitUntil().taskIsCreated(elementId);

    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");

    //then
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.tasks.length()"));
    for (int i=0; i < 3; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertNotNull(response.get(taskJsonPath + ".id"));

      //workflow does not contain task name and workflow name
      assertEquals(elementId, response.get(taskJsonPath + ".name"));
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
    final Comparator<TaskDTO> comparator = Comparator.comparing(TaskDTO::getCreationTime).reversed();
    assertThat(tasks).isSortedAccordingTo(comparator);
  }

  @Test
  public void shouldReturnCompletedTask() throws IOException {
    //having
    final String bpmnProcessId = "testProcess";
    final String elementId = "taskA";
    tester.createAndDeploySimpleWorkflow(bpmnProcessId, elementId)
        .waitUntil()
        .workflowIsDeployed()
        .and()
        .startWorkflowInstance(bpmnProcessId)
        .and()
        .completeHumanTask(elementId);

    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");

    //then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertNotNull(response.get("$.data.tasks[0].id"));
    assertEquals(elementId, response.get("$.data.tasks[0].name"));
    assertEquals(bpmnProcessId, response.get("$.data.tasks[0].workflowName"));
    assertNotNull(response.get("$.data.tasks[0].creationTime"));
    assertNotNull(response.get("$.data.tasks[0].completionTime"));
    assertEquals(TaskState.COMPLETED.name(), response.get("$.data.tasks[0].taskState"));
    assertNull(response.get("$.data.tasks[0].assignee"));
    assertEquals("0", response.get("$.data.tasks[0].variables.length()"));
  }

  @Test
  public void shouldNotImportWrongJobType() throws IOException {
    //having
    final String bpmnProcessId = "testProcess";
    final String wrongElementId = "taskA";
    final String wrongJobType = "serviceTask";
    final BpmnModelInstance workflow = Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .serviceTask(wrongElementId).zeebeJobType(wrongJobType)
        .endEvent().done();

    tester.deployWorkflow(workflow, bpmnProcessId + ".bpmn")
        .waitUntil()
        .workflowIsDeployed()
        .and()
        .startWorkflowInstance(bpmnProcessId)
        .waitUntil()
        .taskIsCreated(wrongElementId);     //this waiting must time out

    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.tasks.length()"));
  }

  @Test
  public void shouldReturnAllOpenTasks() throws IOException {
    //having
    createCreatedAndCompletedTasks(2, 3);
    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-created-tasks.graphql");

    //then
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));
    for (int i=0; i < 2; i++) {
      assertEquals(TaskState.CREATED.name(), response.get(String.format("$.data.tasks[%d].taskState", i)));
    }
  }

  @Test
  public void shouldReturnAllCompletedTasks() throws IOException {
    //having
    createCreatedAndCompletedTasks(2, 3);
    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-completed-tasks.graphql");

    //then
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.tasks.length()"));
    for (int i=0; i < 3; i++) {
      assertEquals(TaskState.COMPLETED.name(), response.get(String.format("$.data.tasks[%d].taskState", i)));
    }
  }

  //TODO #47
  // test Unclaimed and Claimed by me filters
  // test tasks claimed by different users

  @Test
  public void shouldReturnWorkflowAndTaskName() throws IOException {
    //having
    final String workflowName = "Test process name";
    final String taskName = "Task A";
    final String bpmnProcessId = "testProcess";
    final String taskId = "taskA";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(bpmnProcessId).name(workflowName)
        .startEvent("start")
        .serviceTask(taskId).name(taskName).zeebeJobType(tasklistProperties.getImporter().getJobType())
        .endEvent()
        .done();

    tester.deployWorkflow(workflow, "testWorkflow.bpmn")
        .waitUntil()
        .workflowIsDeployed()
        .and()
        .startWorkflowInstance(bpmnProcessId)
        .waitUntil()
        .taskIsCreated(taskId);

    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");

    //then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertEquals(taskName, response.get("$.data.tasks[0].name"));
    assertEquals(workflowName, response.get("$.data.tasks[0].workflowName"));
  }

  @Test
  public void shouldFallbackToTaskIdAndBpmnProcessId() throws IOException {
    //having
    final String bpmnProcessId = "testProcess";
    final String taskId = "taskA";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(bpmnProcessId).name("Test process name")
        .startEvent("start")
        .serviceTask(taskId).name("Task A").zeebeJobType(tasklistProperties.getImporter().getJobType())
        .endEvent()
        .done();

    tester.deployWorkflow(workflow, "testWorkflow.bpmn")
        .and()
        .startWorkflowInstance(bpmnProcessId);
    //load all but workflow
    elasticsearchTestRule.processRecordsWithTypeAndWait(ImportValueType.JOB, taskIsCreatedCheck, tester.getWorkflowInstanceId(), taskId);

    //when
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");

    //then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));
    assertEquals(taskId, response.get("$.data.tasks[0].name"));
    assertEquals(bpmnProcessId, response.get("$.data.tasks[0].workflowName"));
  }

  @Test
  public void shouldReturnOneTask() throws IOException {
    //having
    createCreatedAndCompletedTasks(1, 0);
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    //when
    final GraphQLResponse taskResponse = graphQLTestTemplate.postMultipart(
        String.format(GET_TASK_QUERY_PATTERN, taskId), "{}");

    //then
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
    //having
    createCreatedAndCompletedTasks(1, 0);
    final String taskId = "wrongTaskId";

    //when
    final GraphQLResponse taskResponse = graphQLTestTemplate.postMultipart(
        String.format(GET_TASK_QUERY_PATTERN, taskId), "{}");

    //then
    assertNull(taskResponse.get("$.data"));
    assertEquals("1", taskResponse.get("$.errors.length()"));
    assertEquals("Task with id wrongTaskId was not found", taskResponse.get("$.errors[0].message"));
  }

  private void createCreatedAndCompletedTasks(int created, int completed) {
    tester.createAndDeploySimpleWorkflow(BPMN_PROCESS_ID, ELEMENT_ID)
        .waitUntil()
        .workflowIsDeployed()
        .and();
    //complete tasks
    for (int i = 0; i < completed; i++) {
        tester.startWorkflowInstance(BPMN_PROCESS_ID)
        .and()
        .completeHumanTask(ELEMENT_ID);
    }
    //start more workflow instances
    for (int i = 0; i < created; i++) {
      tester.startWorkflowInstance(BPMN_PROCESS_ID)
          .waitUntil()
          .taskIsCreated(ELEMENT_ID);
    }
  }

}
