/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport;

import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.WORKFLOW_IS_DEPLOYED_CHECK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.util.ElasticsearchChecks.TestCheck;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ZeebeImportIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(WORKFLOW_IS_DEPLOYED_CHECK)
  private TestCheck workflowIsDeployedCheck;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Test
  public void shouldImportAllTasks() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    tester.createAndDeploySimpleWorkflow(bpmnProcessId, flowNodeBpmnId);
    processAllRecordsAndWait(workflowIsDeployedCheck, tester.getWorkflowId());
    tester
        .startWorkflowInstance(bpmnProcessId)
        .startWorkflowInstance(bpmnProcessId)
        .startWorkflowInstance(bpmnProcessId);
    processAllRecordsAndWait(taskIsCreatedCheck, tester.getWorkflowInstanceId(), flowNodeBpmnId);
    final GraphQLResponse response = tester.getAllTasks();

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
  }

  protected void processAllRecordsAndWait(TestCheck testCheck, Object... arguments) {
    elasticsearchTestRule.processRecordsAndWaitFor(
        recordsReaderHolder.getActiveRecordsReaders(), testCheck, null, arguments);
  }
}
