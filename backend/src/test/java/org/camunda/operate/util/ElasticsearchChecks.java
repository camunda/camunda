/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.util;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.client.transport.TransportClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

@Configuration
public class ElasticsearchChecks {

  @Autowired
  private TransportClient esClient;

  @Autowired
  private WorkflowReader workflowReader;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

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

  @Bean(name = "activityIsActiveCheck")
  public Predicate<Object[]> getActivityIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(String.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      String activityId = (String)objects[1];
      try {
        final WorkflowInstanceEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        final List<ActivityInstanceEntity> activities = instance.getActivities().stream().filter(a -> a.getActivityId().equals(activityId))
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

  @Bean(name = "activityIsCompletedCheck")
  public Predicate<Object[]> getActivityIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(String.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      String activityId = (String)objects[1];
      try {
        final WorkflowInstanceEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        final List<ActivityInstanceEntity> activities = instance.getActivities().stream().filter(a -> a.getActivityId().equals(activityId))
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

  @Bean(name = "activityIsTerminatedCheck")
  public Predicate<Object[]> getActivityIsTerminatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(String.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      String activityId = (String)objects[1];
      try {
        final WorkflowInstanceEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        final List<ActivityInstanceEntity> activities = instance.getActivities().stream().filter(a -> a.getActivityId().equals(activityId))
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

  @Bean(name = "incidentIsActiveCheck")
  public Predicate<Object[]> getIncidentIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      try {
        final WorkflowInstanceEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        if (instance.getIncidents().size() == 0) {
          return false;
        } else {
          return instance.getIncidents().get(0).getState().equals(IncidentState.ACTIVE);
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "workflowInstanceIsCanceledCheck")
  public Predicate<Object[]> getWorkflowInstanceIsCanceledCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      try {
        final WorkflowInstanceEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        return instance.getState().equals(WorkflowInstanceState.CANCELED);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "workflowInstanceIsCompletedCheck")
  public Predicate<Object[]> getWorkflowInstanceIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(String.class);
      String workflowInstanceId = (String)objects[0];
      try {
        final WorkflowInstanceEntity instance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
        return instance.getState().equals(WorkflowInstanceState.COMPLETED);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

}
