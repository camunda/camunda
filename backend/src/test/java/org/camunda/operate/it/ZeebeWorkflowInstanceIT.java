package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.List;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.TopicSubscription;
import static org.assertj.core.api.Assertions.assertThat;

public class ZeebeWorkflowInstanceIT extends OperateIntegrationTest {

  @Rule
  public ZeebeTestRule zeebeTestRule = new ZeebeTestRule();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private ZeebeUtil zeebeUtil;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  private JobWorker jobWorker;

  @After
  public void cleanup() {
    if (jobWorker != null && jobWorker.isOpen()) {
      jobWorker.close();
      jobWorker = null;
    }
  }

  @Test
  public void testWorkflowInstanceCreated() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();
    String topicName = zeebeTestRule.getTopicName();


    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");

    //when
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //when
    elasticsearchTestRule.processAllEvents(2);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

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
    jobWorker = zeebeUtil.completeTaskWithIncident(topicName, activityId, zeebeTestRule.getWorkerName());
    elasticsearchTestRule.processAllEvents(5);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getActivityInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotEmpty();
  }

  @Test
  public void testWorkflowInstanceCanceled() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "taskA";


    String processId = "demoProcess";
    zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");
    jobWorker = zeebeUtil.completeTaskWithIncident(topicName, activityId, zeebeTestRule.getWorkerName());

    TopicSubscription topicSubscription = null;
    try {
      //when
      topicSubscription = zeebeUtil.cancelWorkflowInstance(topicName, workflowInstanceId);
      elasticsearchTestRule.processAllEvents(6);

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
    } finally {
      if (topicSubscription != null) {
        topicSubscription.close();
      }
    }

  }

}