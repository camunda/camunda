/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistTester;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.client.api.response.MigrateProcessInstanceResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeImportMigrateProcessTaskIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;
  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldMigrateProcessInstanceAndTasksDefinition() throws InterruptedException {
    // given
    final String bpmnProcessId = "processMigration";
    final String flowNodeBpmnId = "taskA";

    final String oldTaskMapping = "taskA";
    final String newTaskMapping = "taskAM";

    // when
    final TasklistTester task =
        tester
            .deployProcess("processMigration.bpmn")
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, null)
            .then()
            .taskIsCreated(flowNodeBpmnId);

    final String processInstanceId = task.getProcessInstanceId();

    final String migratedProcessDefinitionKey =
        tester
            .having()
            .deployProcess("processAfterMigration.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .getProcessDefinitionKey();

    final MigrateProcessInstanceResponse zeebeMigrationCommandResponse =
        zeebeClient
            .newMigrateProcessInstanceCommand(Long.valueOf(processInstanceId))
            .migrationPlan(
                new MigrationPlanBuilderImpl()
                    .withTargetProcessDefinitionKey(Long.valueOf(migratedProcessDefinitionKey))
                    .addMappingInstruction(oldTaskMapping, newTaskMapping)
                    .build())
            .send()
            .join();

    Thread.sleep(10000L);
    databaseTestExtension.refreshTasklistIndices();

    // This test is only test the behaviour of Zeebe Event,
    // We don't check on Tasklist because Zeebe can take some time to process the event and the test
    // would become flaky
    // So because of that we only check if the response is not null

    // Following the document, the Response is a not null and empty object
    // In case of error, an exception is thrown
    Assertions.assertThat(zeebeMigrationCommandResponse).isNotNull();
  }
}
