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

import static io.camunda.tasklist.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.resolvers.Mutations;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceMutationIT extends TasklistZeebeIntegrationTest {

  private static final List<Class<?>> SHOULD_PROCESS_INSTANCE_DEPENDANTS =
      List.of(FlowNodeInstanceIndex.class, VariableIndex.class, TaskTemplate.class);

  @Autowired private Mutations mutations;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired private TaskVariableTemplate taskVariableIndex;

  @Autowired private NoSqlHelper noSqlHelper;

  @BeforeEach
  public void before() {
    super.before();
  }

  @Test
  public void notExistingProcessInstanceCantBeDeleted() {
    // Given nothing
    // when
    final Boolean deleted = tester.deleteProcessInstance("235");
    // then
    assertThat(deleted).isFalse();
  }

  @Test
  public void completedProcessInstanceCanBeDeleted() {
    // given
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String processInstanceId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .claimAndCompleteHumanTask(
                flowNodeBpmnId, "delete", "\"me\"", "when", "\"processInstance is completed\"")
            .then()
            .waitUntil()
            .processInstanceIsCompleted()
            .getProcessInstanceId();
    // when
    final Boolean deleted = tester.deleteProcessInstance(processInstanceId);
    // then
    assertThat(deleted).isTrue();

    databaseTestExtension.refreshIndexesInElasticsearch();

    assertWhoIsAProcessInstanceDependant();
    assertThatProcessDependantsAreDeleted(processInstanceId);
    assertThatVariablesForTasksOfProcessInstancesAreDeleted();
  }

  @Test
  public void notCompletedProcessInstanceCantBeDeleted() {
    // given
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String processInstanceId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .getProcessInstanceId();
    // when
    final Boolean deleted = tester.deleteProcessInstance(processInstanceId);
    // then
    assertThat(deleted).isFalse();
  }

  protected void assertThatProcessDependantsAreDeleted(String processInstanceId) {
    assertThrows(
        NotFoundApiException.class,
        () -> {
          noSqlHelper.getProcessInstance(processInstanceId);
        });
  }

  protected void assertThatVariablesForTasksOfProcessInstancesAreDeleted() {
    assertThat(noSqlHelper.countIndexResult(taskVariableIndex.getFullQualifiedName())).isZero();
  }

  protected void assertWhoIsAProcessInstanceDependant() {
    final List<Class<?>> currentDependants = map(processInstanceDependants, Object::getClass);
    assertThat(currentDependants).hasSameElementsAs(SHOULD_PROCESS_INSTANCE_DEPENDANTS);
  }
}
