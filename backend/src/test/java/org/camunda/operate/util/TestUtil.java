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
import java.util.function.Consumer;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;

public abstract class TestUtil {

  public static final String ERROR_MSG = "No more retries left.";
  private static Random random = new Random();

  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstance(WorkflowInstanceState state) {
    return createWorkflowInstance(state, null);
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstance(WorkflowInstanceState state, String workflowId) {
    WorkflowInstanceForListViewEntity workflowInstance = new WorkflowInstanceForListViewEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(state);
    workflowInstance.setWorkflowId(workflowId);
    return workflowInstance;
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstance(OffsetDateTime startDate, OffsetDateTime endDate) {
    WorkflowInstanceForListViewEntity workflowInstance = new WorkflowInstanceForListViewEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
    workflowInstance.setStartDate(startDate);
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    if (endDate != null) {
      workflowInstance.setEndDate(endDate);
      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
    }
    return workflowInstance;
  }

  public static ActivityInstanceForListViewEntity createActivityInstanceWithIncident(String workflowInstanceId, ActivityState state, String errorMsg, Long incidentKey) {
    ActivityInstanceForListViewEntity activityInstanceForListViewEntity = createActivityInstance(workflowInstanceId, state);
    createIncident(activityInstanceForListViewEntity, errorMsg, incidentKey);
    return activityInstanceForListViewEntity;
  }

  public static void createIncident(ActivityInstanceForListViewEntity activityInstanceForListViewEntity, String errorMsg, Long incidentKey) {
    if (incidentKey != null) {
      activityInstanceForListViewEntity.setIncidentKey(incidentKey);
    } else {
      activityInstanceForListViewEntity.setIncidentKey((long)random.nextInt());
    }
    if (errorMsg != null) {
      activityInstanceForListViewEntity.setErrorMessage(errorMsg);
    } else {
      activityInstanceForListViewEntity.setErrorMessage(ERROR_MSG);
    }
  }

  public static ActivityInstanceForListViewEntity createActivityInstance(String workflowInstanceId, ActivityState state) {
    return createActivityInstance(workflowInstanceId, state, "start", null);
  }

  public static ActivityInstanceForListViewEntity createActivityInstance(String workflowInstanceId, ActivityState state, String activityId, ActivityType activityType) {
    ActivityInstanceForListViewEntity activityInstanceEntity = new ActivityInstanceForListViewEntity();
    activityInstanceEntity.setWorkflowInstanceId(workflowInstanceId);
    activityInstanceEntity.setId(UUID.randomUUID().toString());
    activityInstanceEntity.setActivityId(activityId);
    activityInstanceEntity.setActivityType(activityType);
    activityInstanceEntity.setActivityState(state);
    activityInstanceEntity.getJoinRelation().setParent(workflowInstanceId);
    return activityInstanceEntity;
  }

  public static ActivityInstanceForListViewEntity createActivityInstance(String workflowInstanceId, ActivityState state, String activityId) {
    return createActivityInstance(workflowInstanceId, state, activityId, ActivityType.SERVICE_TASK);
  }


  public static WorkflowInstanceEntity createWorkflowInstanceEntity(WorkflowInstanceState state) {
    return createWorkflowInstanceEntity(state, null);
  }

  public static WorkflowInstanceEntity createWorkflowInstanceEntity(WorkflowInstanceState state, String workflowId) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(state);
    workflowInstance.setWorkflowId(workflowId);
    return workflowInstance;
  }

  public static WorkflowInstanceEntity createWorkflowInstanceEntity(OffsetDateTime startDate, OffsetDateTime endDate) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
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

  public static ActivityInstanceEntity createActivityInstanceEntity(ActivityState state) {
    return createActivityInstanceEntity(state, "start", null);
  }

  public static ActivityInstanceEntity createActivityInstanceEntity(ActivityState state, String activityId, ActivityType activityType) {
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

  public static ListViewRequestDto createWorkflowInstanceQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewRequestDto request = new ListViewRequestDto();
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    request.getQueries().add(query);
    return request;
  }

  public static ListViewRequestDto createGetAllWorkflowInstancesQuery() {
    return
      createWorkflowInstanceQuery(q -> {
        q.setRunning(true);
        q.setActive(true);
        q.setIncidents(true);
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }

  public static ListViewRequestDto createGetAllWorkflowInstancesQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewRequestDto workflowInstanceQuery = createGetAllWorkflowInstancesQuery();
    filtersSupplier.accept(workflowInstanceQuery.getQueries().get(0));

    return workflowInstanceQuery;
  }

  public static ListViewRequestDto createGetAllFinishedQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewRequestDto workflowInstanceQuery = createGetAllFinishedQuery();
    filtersSupplier.accept(workflowInstanceQuery.getQueries().get(0));
    return workflowInstanceQuery;
  }

  public static ListViewRequestDto createGetAllFinishedQuery() {
    return
      createWorkflowInstanceQuery(q -> {
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }
}
