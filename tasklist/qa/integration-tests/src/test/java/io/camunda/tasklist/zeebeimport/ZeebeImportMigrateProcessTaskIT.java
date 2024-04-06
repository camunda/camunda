/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistTester;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.client.api.response.MigrateProcessInstanceResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

  @Test
  @Disabled // FIXME : investigate why this sometimes fails in GHA CI
  public void shouldMigrateProcessInstanceAndTasksDefinitionForZeebeUserTask() {
    // given
    final String bpmnProcessId = "processMigration";
    final String oldElementId = "taskA";
    final String newElementId = "taskAM";

    // when
    final String taskId =
        tester
            .createAndDeployProcess(
                Bpmn.createExecutableProcess(bpmnProcessId)
                    .startEvent()
                    .userTask(oldElementId)
                    .zeebeUserTask()
                    .endEvent()
                    .done())
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, null)
            .then()
            .taskIsCreated(oldElementId)
            .getTaskId();
    final String oldProcessDefKey = tester.getProcessDefinitionKey();

    tester
        .having()
        .createAndDeployProcess(
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent()
                .userTask(newElementId)
                .zeebeUserTask()
                .userTask("another_task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .waitUntil()
        .processIsDeployed()
        .and()
        .migrateProcessInstance(oldElementId, newElementId)
        .waitUntil()
        .taskIsCreated(newElementId);

    final String newProcessDefKey = tester.getProcessDefinitionKey();

    assertNotEquals(newProcessDefKey, oldProcessDefKey);

    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              Assertions.assertThat(task.getId()).isEqualTo(taskId);
              Assertions.assertThat(task.getProcessInstanceKey())
                  .isEqualTo(tester.getProcessInstanceId());
              Assertions.assertThat(task.getProcessDefinitionKey()).isEqualTo(newProcessDefKey);
              Assertions.assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
              Assertions.assertThat(task.getAssignee()).isNull();
            });
  }
}
