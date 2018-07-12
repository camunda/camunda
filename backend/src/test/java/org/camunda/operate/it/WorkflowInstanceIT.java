package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.TopicSubscription;
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

  private JobWorker jobWorker;

  private List<TopicSubscription> topicSubscriptions = new ArrayList<>();

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    testStartTime = OffsetDateTime.now();
  }

  @After
  public void cleanup() {

    for (Iterator<TopicSubscription> iterator = topicSubscriptions.iterator(); iterator.hasNext(); ) {
      iterator.next().close();
      iterator.remove();
    }

    if (jobWorker != null && jobWorker.isOpen()) {
      jobWorker.close();
      jobWorker = null;
    }
  }

  @Test
  public void testWorkflowInstanceCreated() {
    // having
    String topicName = zeebeTestRule.getTopicName();


    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");

    //when
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //when
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
    jobWorker = zeebeUtil.failTask(topicName, activityId, zeebeTestRule.getWorkerName(), 3);
    elasticsearchTestRule.processAllEvents(18);
    jobWorker.close();
    jobWorker = null;

    //when update retries
    topicSubscriptions.add(zeebeUtil.resolveIncident(topicName, "testIncidentDeleted", workflowId, "{\"a\": \"b\"}"));
    elasticsearchTestRule.processAllEvents(4);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.DELETED);
  }

  @Test
  public void testWorkflowInstanceWithIncidentCreated() {
    // having
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "taskA";


    String processId = "demoProcess";
    zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //when
    jobWorker = zeebeUtil.completeTask(topicName, activityId, zeebeTestRule.getWorkerName(), null);      //empty payload provokes incident
    elasticsearchTestRule.processAllEvents(16);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
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
    assertActivityIsActive(workflowInstanceEntity.getActivities().get(1), "taskA");

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
    topicSubscriptions.add(zeebeUtil.cancelWorkflowInstance(topicName, workflowInstanceId, workflowId));
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

}