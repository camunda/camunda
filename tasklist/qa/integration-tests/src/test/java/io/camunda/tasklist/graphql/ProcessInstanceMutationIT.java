/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.tasklist.schema.v86.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.v86.indices.VariableIndex;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceMutationIT extends TasklistZeebeIntegrationTest {

  private static final List<Class<?>> SHOULD_PROCESS_INSTANCE_DEPENDANTS =
      List.of(FlowNodeInstanceIndex.class, VariableIndex.class, TaskTemplate.class);

  @Autowired private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired private TaskVariableTemplate taskVariableIndex;

  @Autowired private NoSqlHelper noSqlHelper;

  @Override
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
