/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.ProcessInstanceState;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = TasklistProperties.PREFIX,
    name = "webappEnabled",
    havingValue = "true",
    matchIfMissing = true)
public class ElasticsearchChecks {

  public static final String PROCESS_IS_DEPLOYED_CHECK = "processIsDeployedCheck";
  public static final String PROCESS_INSTANCE_IS_COMPLETED_CHECK =
      "processInstanceIsCompletedCheck";
  public static final String PROCESS_INSTANCE_IS_CANCELED_CHECK = "processInstanceIsCanceledCheck";

  public static final String TASK_IS_CREATED_CHECK = "taskIsCreatedCheck";
  public static final String TASK_IS_ASSIGNED_CHECK = "taskIsAssignedCheck";

  public static final String TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "taskIsCreatedByFlowNodeBpmnIdCheck";
  public static final String TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "tasksAreCreatedByFlowNodeBpmnIdCheck";
  public static final String TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "taskIsCanceledByFlowNodeBpmnIdCheck";
  public static final String TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "taskIsCompletedByFlowNodeBpmnIdCheck";
  public static final String TASK_VARIABLE_EXISTS_CHECK = "taskVariableExistsCheck";
  public static final String VARIABLES_EXIST_CHECK = "variablesExistsCheck";

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchChecks.class);

  @Autowired private ElasticsearchHelper elasticsearchHelper;

  @Autowired private ProcessReader processReader;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  /** Checks whether the process of given args[0] processId (Long) is deployed. */
  @Bean(name = PROCESS_IS_DEPLOYED_CHECK)
  public TestCheck getProcessIsDeployedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return PROCESS_IS_DEPLOYED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String processId = (String) objects[0];
        try {
          final ProcessEntity process = processReader.getProcess(processId);
          return process != null;
        } catch (TasklistRuntimeException ex) {
          return false;
        }
      }
    };
  }

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
          final TaskEntity taskEntity = elasticsearchHelper.getTask(taskId);
          return TaskState.CREATED.equals(taskEntity.getState());
        } catch (NotFoundException ex) {
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
          final TaskEntity taskEntity = elasticsearchHelper.getTask(taskId);
          return taskEntity.getAssignee() != null;
        } catch (NotFoundException ex) {
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
              elasticsearchHelper.getTask(processInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CREATED);
        } catch (NotFoundException ex) {
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
          final List<TaskEntity> tasks = elasticsearchHelper.getTask(null, flowNodeBpmnId);
          return (tasks.size() == taskCount)
              && (tasks.stream()
                  .map(TaskEntity::getState)
                  .collect(Collectors.toList())
                  .contains(TaskState.CREATED));
        } catch (NotFoundException ex) {
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
              elasticsearchHelper.getTask(processInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CANCELED);
        } catch (NotFoundException ex) {
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
              elasticsearchHelper.getTask(processInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.COMPLETED);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the process instance for given args[0] processInstanceId (String) is completed.
   */
  @Bean(name = PROCESS_INSTANCE_IS_COMPLETED_CHECK)
  public TestCheck getProcessInstanceIsCompletedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return PROCESS_INSTANCE_IS_COMPLETED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String processInstanceId = (String) objects[0];
        try {
          final ProcessInstanceEntity wfiEntity =
              elasticsearchHelper.getProcessInstance(processInstanceId);
          return ProcessInstanceState.COMPLETED.equals(wfiEntity.getState());
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the process instance for given args[0] processInstanceId (String) is canceled.
   */
  @Bean(name = PROCESS_INSTANCE_IS_CANCELED_CHECK)
  public TestCheck getProcessInstanceIsCanceledCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return PROCESS_INSTANCE_IS_CANCELED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String processInstanceId = (String) objects[0];
        try {
          final ProcessInstanceEntity wfiEntity =
              elasticsearchHelper.getProcessInstance(processInstanceId);
          return ProcessInstanceState.CANCELED.equals(wfiEntity.getState());
        } catch (NotFoundException ex) {
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
          return elasticsearchHelper.checkVariableExists(taskId, varName);
        } catch (NotFoundException ex) {
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
        final String[] varNames = (String[]) objects;
        try {
          return elasticsearchHelper.checkVariablesExist(varNames);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  public interface TestCheck extends Predicate<Object[]> {
    String getName();
  }
}
