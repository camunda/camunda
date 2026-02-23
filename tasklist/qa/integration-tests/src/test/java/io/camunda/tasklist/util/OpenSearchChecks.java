/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TestCheck.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.conditions.ConditionalOnWebappEnabled;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebappEnabled("tasklist")
@Conditional(OpenSearchCondition.class)
public class OpenSearchChecks {

  @Autowired private OpenSearchHelper openSearchHelper;

  /** Checks whether the task for given args[0] taskId (String) exists and is in state CREATED. */
  @Bean(name = TASK_IS_CREATED_CHECK)
  public TestCheck getTaskIsCreatedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CREATED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String taskId = (String) objects[0];
        try {
          final TaskEntity taskEntity = openSearchHelper.getTask(taskId);
          return TaskState.CREATED.equals(taskEntity.getState());
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  @Bean(name = TASK_HAS_CANDIDATE_USERS)
  public TestCheck getTaskHasCandidateUsers() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_HAS_CANDIDATE_USERS;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String processInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final String taskId = (String) objects[0];
          final TaskEntity taskEntity = openSearchHelper.getTask(taskId);
          return taskEntity.getCandidateGroups().length > 0;
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /** Checks whether the task for given args[0] taskId (String) exists and is assigned. */
  @Bean(name = TASK_IS_ASSIGNED_CHECK)
  public TestCheck getTaskIsAssignedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_ASSIGNED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String taskId = (String) objects[0];
        try {
          final TaskEntity taskEntity = openSearchHelper.getTask(taskId);
          return taskEntity.getAssignee() != null;
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] processInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state CREATED.
   */
  @Bean(name = TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public TestCheck getTaskIsCreatedByFlowNodeBpmnIdCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String processInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity =
              openSearchHelper.getTask(processInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CREATED);
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the tasks for given args[0] flowNodeBpmnId (String) exist and are in state
   * CREATED.
   */
  @Bean(name = TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public TestCheck getTasksAreCreatedByFlowNodeBpmnIdCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(Integer.class);
        final String flowNodeBpmnId = (String) objects[0];
        final int taskCount = (Integer) objects[1];
        try {
          final List<TaskEntity> tasks = openSearchHelper.getTask(null, flowNodeBpmnId);
          return (tasks.size() == taskCount)
              && (tasks.stream()
                  .map(TaskEntity::getState)
                  .collect(Collectors.toList())
                  .contains(TaskState.CREATED));
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] processInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state CANCELED.
   */
  @Bean(name = TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public TestCheck getTaskIsCanceledCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String processInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity =
              openSearchHelper.getTask(processInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CANCELED);
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] processInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state COMPLETED.
   */
  @Bean(name = TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public Predicate<Object[]> getTaskIsCompletedByFlowNodeBpmnIdCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String processInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity =
              openSearchHelper.getTask(processInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.COMPLETED);
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /** Checks whether the task variable exists. */
  @Bean(name = TASK_VARIABLE_EXISTS_CHECK)
  public TestCheck getVariableExistsCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_VARIABLE_EXISTS_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String taskId = (String) objects[0];
        final String varName = (String) objects[1];
        try {
          return openSearchHelper.checkTaskVariableExists(taskId, varName);
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }

  /** Checks whether the task variable exists. */
  @Bean(name = VARIABLES_EXIST_CHECK)
  public TestCheck getVariablesExistCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return VARIABLES_EXIST_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String[].class);
        final String processInstanceId = (String) objects[0];
        final String[] varNames = (String[]) objects[1];
        try {
          return openSearchHelper.checkVariablesExist(processInstanceId, varNames);
        } catch (final NotFoundApiException ex) {
          return false;
        }
      }
    };
  }
}
