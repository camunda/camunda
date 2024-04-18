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
package io.camunda.tasklist.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class FormIT extends TasklistZeebeIntegrationTest {

  @Test
  public void shouldReturnForm() throws IOException {
    final String formKey = "camunda-forms:bpmn:userTask:Form_1";
    final String formId = "userTask:Form_1";
    createData();

    final GraphQLResponse response = tester.when().getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 2; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertEquals(formKey, response.get(taskJsonPath + ".formKey"));
      assertEquals(
          tester.getProcessDefinitionKey(), response.get(taskJsonPath + ".processDefinitionId"));
    }

    // when - get form
    final GraphQLResponse formResponse = tester.getForm(formId);

    // then
    assertTrue(formResponse.isOk());
    assertEquals(formId, formResponse.get("$.data.form.id"));
    assertEquals(
        tester.getProcessDefinitionKey(), formResponse.get("$.data.form.processDefinitionId"));
    assertNotNull(formResponse.get("$.data.form.schema"));
  }

  @Test
  public void shouldFailOnNonExistingForm() throws IOException {
    createData();

    // when
    final var bpmnFormId = "wrongId";
    final GraphQLResponse formResponse = tester.getForm(bpmnFormId);

    // then
    final var expectedErrorMessage = String.format("form with id %s was not found", bpmnFormId);
    assertEquals(expectedErrorMessage, formResponse.get("$.errors[0].message"));
  }

  private void createData() {
    final String bpmnProcessId = "userTaskFormProcess";
    final String flowNodeBpmnId = "taskA";

    tester
        .having()
        .deployProcess("userTaskForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 2)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 2);
  }
}
