/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.VariableReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.dto.OperationDto;
import org.camunda.operate.rest.dto.VariableDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.longrunning.Operation;

import static org.assertj.core.api.Assertions.assertThat;

@Configuration
public class ElasticsearchChecks {

  @Autowired
  private WorkflowReader workflowReader;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private VariableReader variableReader;

  /**
   * Checks whether the workflow of given args[0] workflowId (String) is deployed.
   * @return
   */
  @Bean(name = "workflowIsDeployedCheck")
  public Predicate<Object[]> getWorkflowIsDeployedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(String.class);
      String workflowId = (String)objects[0];
      try {
        final WorkflowEntity workflow = workflowReader.getWorkflow(workflowId);
        return workflow != null;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the activity of given args[0] workflowInstanceId (Long) and args[1] activityId (String) is in state ACTIVE
   * @return
   */
  @Bean(name = "activityIsActiveCheck")
  public Predicate<Object[]> getActivityIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      String activityId = (String)objects[1];
      try {
        List<ActivityInstanceEntity> activityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
        final List<ActivityInstanceEntity> activities = activityInstances.stream().filter(a -> a.getActivityId().equals(activityId))
          .collect(Collectors.toList());
        if (activities.size() == 0) {
          return false;
        } else {
          return activities.get(0).getState().equals(ActivityState.ACTIVE);
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether variable of given args[0] workflowInstanceId  (Long) and args[1] scopeId (Long) and args[2] varName (String) exists
   * @return
   */
  @Bean(name = "variableExistsCheck")
  public Predicate<Object[]> getVariableExistsCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Long.class);
      assertThat(objects[2]).isInstanceOf(String.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      String scopeId = IdTestUtil.getId((Long)objects[1]);
      String varName = (String)objects[2];
      try {
        List<VariableDto> variables = variableReader.getVariables(workflowInstanceId, scopeId);
        return variables.stream().anyMatch(v -> v.getName().equals(varName));
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  //TODO: Check for variable (name AND value)
  /**
   * Checks whether the activity of given args[0] workflowInstanceId (Long) and args[1] activityId (String) is in state COMPLETED
   * @return
   */
  @Bean(name = "activityIsCompletedCheck")
  public Predicate<Object[]> getActivityIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      String activityId = (String)objects[1];
      try {
        List<ActivityInstanceEntity> activityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
        final List<ActivityInstanceEntity> activities = activityInstances.stream().filter(a -> a.getActivityId().equals(activityId))
          .collect(Collectors.toList());
        if (activities.size() == 0) {
          return false;
        } else {
          return activities.get(0).getState().equals(ActivityState.COMPLETED);
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the activity of given args[0] workflowInstanceId (Long) and args[1] activityId (String) is in state TERMINATED
   * @return
   */
  @Bean(name = "activityIsTerminatedCheck")
  public Predicate<Object[]> getActivityIsTerminatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      String activityId = (String)objects[1];
      try {
        List<ActivityInstanceEntity> activityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
        final List<ActivityInstanceEntity> activities = activityInstances.stream().filter(a -> a.getActivityId().equals(activityId))
          .collect(Collectors.toList());
        if (activities.size() == 0) {
          return false;
        } else {
          return activities.get(0).getState().equals(ActivityState.TERMINATED);
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether any incidents is active in workflowInstance of given workflowInstanceId (Long)
   * @return
   */
  @Bean(name = "incidentIsActiveCheck")
  public Predicate<Object[]> getIncidentIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      try {
        final List<ActivityInstanceEntity> allActivityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
        boolean found = allActivityInstances.stream().anyMatch(ai -> ai.getIncidentKey() != null);
        if (found) {
          List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceId);
          found = allIncidents.size() > 0;
        }
        return found;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the incidents of given args[0] workflowInstanceId (Long) equals given args[1] incidentsCount (Integer)
   * @return
   */
  @Bean(name = "incidentsAreActiveCheck")
  public Predicate<Object[]> getIncidentsAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      int incidentsCount = (int)objects[1];
      try {
        final List<ActivityInstanceEntity> allActivityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
        return allActivityInstances.stream().filter(ai -> ai.getIncidentKey() != null).count() == incidentsCount;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether there are no incidents in activities exists in given workflowInstanceId (Long)
   * @return
   */
  @Bean(name = "incidentIsResolvedCheck")
  public Predicate<Object[]> getIncidentIsResolvedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      try {
        final List<ActivityInstanceEntity> allActivityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
        return allActivityInstances.stream().noneMatch(ai -> ai.getIncidentKey() != null);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the workflowInstance of given workflowInstanceId (Long) is CANCELED.
   * @return
   */
  @Bean(name = "workflowInstanceIsCanceledCheck")
  public Predicate<Object[]> getWorkflowInstanceIsCanceledCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      try {
        final WorkflowInstanceForListViewEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        return instance.getState().equals(WorkflowInstanceState.CANCELED);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }
  
  /**
   * Checks whether the workflowInstance of given workflowInstanceId (Long) is CREATED.
   * @return
   */
  @Bean(name = "workflowInstanceIsCreatedCheck")
  public Predicate<Object[]> getWorkflowInstanceIsCreatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      try {
        final WorkflowInstanceForListViewEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        return true;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the workflowInstance of given workflowInstanceId (Long) is COMPLETED.
   * @return
   */
  @Bean(name = "workflowInstanceIsCompletedCheck")
  public Predicate<Object[]> getWorkflowInstanceIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      String workflowInstanceId = IdTestUtil.getId((Long)objects[0]);
      try {
        final WorkflowInstanceForListViewEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        return instance.getState().equals(WorkflowInstanceState.COMPLETED);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether all workflowInstances from given workflowInstanceIds (List<String) are finished
   * @return
   */
  @Bean(name = "workflowInstancesAreFinished")
  public Predicate<Object[]> getWorkflowInstancesAreFinishedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      List<String> ids = (List<String>)objects[0];
      final ListViewRequestDto getFinishedQuery =
        TestUtil.createGetAllFinishedQuery(q -> q.setIds(ids));
      final ListViewResponseDto responseDto = listViewReader.queryWorkflowInstances(getFinishedQuery, 0, ids.size());
      return responseDto.getTotalCount() == ids.size();
    };
  }

  /**
   * Checks whether all workflowInstances from given workflowInstanceIds (List<String) are started
   * @return
   */
  @Bean(name = "workflowInstancesAreStarted")
  public Predicate<Object[]> getWorkflowInstancesAreStartedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      List<String> ids = (List<String>)objects[0];
      final ListViewRequestDto getActiveQuery =
        TestUtil.createWorkflowInstanceQuery(q -> {
          q.setIds(ids);
          q.setRunning(true);
          q.setActive(true);
        });
      final ListViewResponseDto responseDto = listViewReader.queryWorkflowInstances(getActiveQuery, 0, ids.size());
      return responseDto.getTotalCount() == ids.size();
    };
  }
  
  /**
   * Checks whether all operations for given workflowInstanceId (String) are completed
   * @return
   */
  @Bean(name = "operationsByWorkflowInstanceAreCompleted")
  public Predicate<Object[]> getOperationsByWorkflowInstanceAreCompleted() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsById(workflowInstanceId);
      return workflowInstance.getOperations().stream().allMatch( operation -> {
          return operation.getState().equals(OperationState.COMPLETED);
      });
    };
  }

}
