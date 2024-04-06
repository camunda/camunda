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
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TestCheck.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.ProcessInstanceState;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = TasklistProperties.PREFIX,
    name = "webappEnabled",
    havingValue = "true",
    matchIfMissing = true)
@Conditional(OpenSearchCondition.class)
public class OpenSearchChecks {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchChecks.class);

  @Autowired private OpenSearchHelper openSearchHelper;

  @Autowired private ProcessStore processStore;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  /** Checks whether the process of given args[0] processId (Long) is deployed. */
  @Bean(name = PROCESS_IS_DEPLOYED_CHECK)
  public TestCheck getProcessIsDeployedOsCheck() {
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
          final ProcessEntity process = processStore.getProcess(processId);
          return process != null;
        } catch (TasklistRuntimeException ex) {
          return false;
        }
      }
    };
  }

  @Bean(name = PROCESS_IS_DELETED_CHECK)
  public TestCheck getProcessIsDeletedCheck() {

    return new TestCheck() {
      @Override
      public String getName() {
        return PROCESS_IS_DELETED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String processId = (String) objects[0];
        try {
          processStore.getProcess(processId);
          return false;
        } catch (NotFoundException nfe) {
          return true;
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
          final TaskEntity taskEntity = openSearchHelper.getTask(taskId);
          return TaskState.CREATED.equals(taskEntity.getState());
        } catch (NotFoundApiException ex) {
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
        } catch (NotFoundApiException ex) {
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
        } catch (NotFoundApiException ex) {
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
        } catch (NotFoundApiException ex) {
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
        } catch (NotFoundApiException ex) {
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
        } catch (NotFoundApiException ex) {
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
        } catch (NotFoundApiException ex) {
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
              openSearchHelper.getProcessInstance(processInstanceId);
          return ProcessInstanceState.COMPLETED.equals(wfiEntity.getState());
        } catch (NotFoundApiException ex) {
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
              openSearchHelper.getProcessInstance(processInstanceId);
          return ProcessInstanceState.CANCELED.equals(wfiEntity.getState());
        } catch (NotFoundApiException ex) {
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
          return openSearchHelper.checkVariableExists(taskId, varName);
        } catch (NotFoundApiException ex) {
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
          return openSearchHelper.checkVariablesExist(varNames);
        } catch (NotFoundApiException ex) {
          return false;
        }
      }
    };
  }
}
