package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.List;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowInstanceIT extends OperateIntegrationTest {

  @Rule
  public ZeebeTestRule zeebeTestRule = new ZeebeTestRule();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private ZeebeUtil zeebeUtil;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    testStartTime = OffsetDateTime.now();
  }

  @Test
  public void testWorkflowInstanceCreated() {
    // having
    String topicName = zeebeTestRule.getTopicName();


    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");

    //when
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEvents(10);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity fields
    assertThat(workflowInstanceEntity.getActivities().size()).isEqualTo(2);
    assertStartActivityCompleted(workflowInstanceEntity.getActivities().get(0));
    assertActivityIsActive(workflowInstanceEntity.getActivities().get(1), "taskA");

  }

  @Test
  public void testIncidentDeleted() {
    // having
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "taskA";

    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //create an incident
    failTaskWithNoRetriesLeft(activityId);

    //when update retries
    zeebeTestRule.getTopicSubscriptions().add(zeebeUtil.resolveIncident(topicName, "testIncidentDeleted", workflowId, "{\"a\": \"b\"}"));
    elasticsearchTestRule.processAllEvents(4);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.DELETED);
    assertThat(workflowInstanceEntity.getActivities()).filteredOn(ai -> ai.getId().equals(incidentEntity.getActivityInstanceId())).extracting(
      WorkflowInstanceType.STATE).containsOnly(ActivityState.ACTIVE);

  }

  @Test
  public void testWorkflowInstanceWithIncidentCreated() {
    // having
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "taskA";


    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //when
    //create an incident
    failTaskWithNoRetriesLeft(activityId);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getActivityInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotEmpty();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert activity fields
    assertThat(workflowInstanceEntity.getActivities().size()).isEqualTo(2);
    assertStartActivityCompleted(workflowInstanceEntity.getActivities().get(0));
    assertActivityIsInIncidentState(workflowInstanceEntity.getActivities().get(1), "taskA");

  }

  @Test
  public void testWorkflowInstanceWithIncidentOnGateway() {
    // having
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "xor";

    String processId = "demoProcess";
    WorkflowDefinition workflow = Bpmn.createExecutableWorkflow(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
          .serviceTask("task1").taskType("task1").done()
          .endEvent()
        .sequenceFlow("s2", s -> s.condition("$.foo >= 5"))
          .serviceTask("task2").taskType("task2").done()
          .endEvent()
      .done();
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, workflow, processId + ".bpmn");

    //when
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllEvents(16);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then incident created, activity in INCIDENT state
    WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getActivityInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotEmpty();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert activity fields
    assertThat(workflowInstanceEntity.getActivities().size()).isEqualTo(2);
    assertStartActivityCompleted(workflowInstanceEntity.getActivities().get(0));
    final ActivityInstanceEntity gatewayActivity = workflowInstanceEntity.getActivities().get(1);
    assertActivityIsInIncidentState(gatewayActivity, "xor");

    //when payload updated
    zeebeUtil.updatePayload(topicName, gatewayActivity.getId(), workflowInstanceId, "{\"foo\": 7}", processId, workflowId);
    elasticsearchTestRule.processAllEvents(5);

    //then incident is resolved
    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    //assert activity fields
    final ActivityInstanceEntity xorActivity = workflowInstanceEntity.getActivities().stream().filter(a -> a.getActivityId().equals("xor"))
      .findFirst().get();
    assertThat(xorActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(xorActivity.getEndDate()).isNotNull();
  }

  @Test
  public void testWorkflowInstanceCanceled() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();
    String topicName = zeebeTestRule.getTopicName();

    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //when
    zeebeTestRule.getTopicSubscriptions().add(zeebeUtil.cancelWorkflowInstance(topicName, workflowInstanceId, workflowId));
    elasticsearchTestRule.processAllEvents(15);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceEntity.getEndDate()).isNotNull();
    assertThat(workflowInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    final List<ActivityInstanceEntity> activities = workflowInstanceEntity.getActivities();
    assertThat(activities.size()).isGreaterThan(0);
    final ActivityInstanceEntity lastActivity = activities.get(activities.size() - 1);
    assertThat(lastActivity.getState().equals(ActivityState.TERMINATED));
    assertThat(lastActivity.getEndDate()).isNotNull();
    assertThat(lastActivity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(lastActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  private void assertStartActivityCompleted(ActivityInstanceEntity startActivity) {
    assertThat(startActivity.getActivityId()).isEqualTo("start");
    assertThat(startActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsActive(ActivityInstanceEntity activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVE);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isNull();
  }

  private void assertActivityIsInIncidentState(ActivityInstanceEntity activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isNull();
  }


  private void failTaskWithNoRetriesLeft(String taskName) {
    zeebeTestRule.setJobWorker(zeebeUtil.failTask(zeebeTestRule.getTopicName(), taskName, zeebeTestRule.getWorkerName(), 3));
    elasticsearchTestRule.processAllEvents(20);
    zeebeTestRule.getJobWorker().close();
    zeebeTestRule.setJobWorker(null);
  }

}