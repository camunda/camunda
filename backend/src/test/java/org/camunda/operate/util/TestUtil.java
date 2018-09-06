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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;

public abstract class TestUtil {

  public static final String ERROR_MSG = "No more retries left.";
  private static Random random = new Random();

  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static WorkflowInstanceEntity createWorkflowInstance(WorkflowInstanceState state) {
    return createWorkflowInstance(state, null);
  }

  public static WorkflowInstanceEntity createWorkflowInstance(WorkflowInstanceState state, String workflowId) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(state);
    workflowInstance.setWorkflowId(workflowId);
    return workflowInstance;
  }

  public static WorkflowInstanceEntity createWorkflowInstance(OffsetDateTime startDate, OffsetDateTime endDate) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(startDate);
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    if (endDate != null) {
      workflowInstance.setEndDate(endDate);
      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
    }
    return workflowInstance;
  }

  public static IncidentEntity createIncident(IncidentState state) {
    return createIncident(state, "start", UUID.randomUUID().toString(), null);
  }

  public static IncidentEntity createIncident(IncidentState state, String errorMsg) {
    return createIncident(state, "start", UUID.randomUUID().toString(), errorMsg);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, String activityInstanceId) {
    return createIncident(state, activityId, activityInstanceId, null);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, String activityInstanceId, String errorMsg) {
    IncidentEntity incidentEntity = new IncidentEntity();
    incidentEntity.setId(UUID.randomUUID().toString());
    incidentEntity.setActivityId(activityId);
    incidentEntity.setActivityInstanceId(activityInstanceId);
    incidentEntity.setErrorType("TASK_NO_RETRIES");
    if (errorMsg == null) {
      incidentEntity.setErrorMessage(ERROR_MSG);
    } else {
      incidentEntity.setErrorMessage(errorMsg);
    }
    incidentEntity.setState(state);
    return incidentEntity;
  }

  public static ActivityInstanceEntity createActivityInstance(ActivityState state) {
    return createActivityInstance(state, "start", ActivityType.START_EVENT);
  }

  public static ActivityInstanceEntity createActivityInstance(ActivityState state, String activityId, ActivityType activityType) {
    ActivityInstanceEntity activityInstanceEntity = new ActivityInstanceEntity();
    activityInstanceEntity.setId(UUID.randomUUID().toString());
    activityInstanceEntity.setActivityId(activityId);
    activityInstanceEntity.setType(activityType);
    activityInstanceEntity.setStartDate(DateUtil.getRandomStartDate());
    activityInstanceEntity.setState(state);
    if (state.equals(ActivityState.COMPLETED) || state.equals(ActivityState.TERMINATED)) {
      activityInstanceEntity.setEndDate(DateUtil.getRandomEndDate());
    }
    return activityInstanceEntity;
  }

  public static List<WorkflowEntity> createWorkflowVersions(String bpmnProcessId, String name, int versionsCount) {
    List<WorkflowEntity> result = new ArrayList<>();
    for (int i = 1; i <= versionsCount; i++) {
      WorkflowEntity workflowEntity = new WorkflowEntity();
      workflowEntity.setId(UUID.randomUUID().toString());
      workflowEntity.setBpmnProcessId(bpmnProcessId);
      workflowEntity.setName(name + i);
      workflowEntity.setVersion(i);
      result.add(workflowEntity);
    }
    return result;
  }

  public static SequenceFlowEntity createSequenceFlow() {
    SequenceFlowEntity sequenceFlowEntity = new SequenceFlowEntity();
    sequenceFlowEntity.setId(UUID.randomUUID().toString());
    sequenceFlowEntity.setActivityId("SequenceFlow_" + random.nextInt());
    return sequenceFlowEntity;
  }

}
