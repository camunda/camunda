package org.camunda.operate.it;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.EventReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
  public void testPayloadUpdated() {
    // having
    String topicName = zeebeTestRule.getTopicName();

    String processId = "demoProcess";
    WorkflowDefinition workflow = Bpmn.createExecutableWorkflow(processId)
      .startEvent("start")
        .serviceTask("task1").taskType("task1").done()
        .serviceTask("task2").taskType("task2")
          .input("$.a", "$.foo")
          .output("$.foo", "$.bar")
        .done()
        .serviceTask("task3").taskType("task3").done()
      .endEvent()
      .done();
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, workflow, processId + ".bpmn");

    //when workflow instance is started
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\", \"nullVar\": null}");
    elasticsearchTestRule.processAllEvents(10);

    //then
    WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertVariable(workflowInstanceEntity, "a","b");

    //when activity with input mapping is activated
    JobWorker jobWorker = zeebeUtil.completeTask(zeebeTestRule.getTopicName(), "task1", zeebeTestRule.getWorkerName(), null);
    elasticsearchTestRule.processAllEvents(11);
    jobWorker.close();

    //then
    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertVariable(workflowInstanceEntity, "foo","b");

    //when activity with output mapping is completed
    jobWorker = zeebeUtil.completeTask(zeebeTestRule.getTopicName(), "task2", zeebeTestRule.getWorkerName(), null);
    elasticsearchTestRule.processAllEvents(11);
    jobWorker.close();

    //then
    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertVariable(workflowInstanceEntity, "bar", "b");
    assertVariable(workflowInstanceEntity, "a","b");

    //when payload is explicitly updated
    final String activityInstanceId = workflowInstanceEntity.getActivities().stream().filter(ai -> ai.getActivityId().equals("task3")).findFirst().get().getId();
    zeebeUtil.updatePayload(zeebeTestRule.getTopicName(), activityInstanceId, workflowInstanceId, "{\"newVar\": 555 }", processId, workflowId);
//    elasticsearchTestRule.processAllEvents(2);

    //then
//    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
//    assertVariable(workflowInstanceEntity, "newVar", 555L);

    //when task is completed with new payload and workflow instance is finished
    jobWorker = zeebeUtil.completeTask(zeebeTestRule.getTopicName(), "task3", zeebeTestRule.getWorkerName(), "{\"task3Completed\": true}");
    elasticsearchTestRule.processAllEvents(12);
    jobWorker.close();

    //then
    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertVariable(workflowInstanceEntity, "task3Completed", true);
    assertVariable(workflowInstanceEntity, "newVar", 555L);

  }

  private EventQueryDto createEventsQuery(String workflowInstanceId, String activityInstanceId) {
    EventQueryDto q = new EventQueryDto();
    q.setWorkflowInstanceId(workflowInstanceId);
    q.setActivityInstanceId(activityInstanceId);
    return q;
  }

  @Test
  public void testVariablesCreated() throws URISyntaxException, IOException {
    // having
    String topicName = zeebeTestRule.getTopicName();

    String payload = new String(Files.readAllBytes(Paths.get(WorkflowInstanceIT.class.getResource("/payload.json").toURI())));
    String processId = "demoProcess";
    zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");

    //when
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, payload);
    elasticsearchTestRule.processAllEvents(10);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);

    assertThat(workflowInstanceEntity.countVariables()).isEqualTo(23);

    assertVariable(workflowInstanceEntity, "objectLevel1.stringFieldL1","stringValue1");
    assertVariable(workflowInstanceEntity, "objectLevel1.stringFieldL1", "stringValue1");
    assertVariable(workflowInstanceEntity, "objectLevel1.intFieldL1", 111L);
    assertVariable(workflowInstanceEntity, "objectLevel1.longFieldL1", 111L);
    assertVariable(workflowInstanceEntity, "objectLevel1.doubleFieldL1", 0.555);
    assertVariable(workflowInstanceEntity, "objectLevel1.booleanFieldL1", true);
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayFieldL1[0]", 1L);
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayFieldL1[1]", 2L);
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayFieldL1[2]", 3L);
    assertVariable(workflowInstanceEntity, "objectLevel1.objectLevel2.stringFieldL2", "stringValue2");
    assertVariable(workflowInstanceEntity, "objectLevel1.objectLevel2.intFieldL2", 222L);
    assertVariable(workflowInstanceEntity, "objectLevel1.objectLevel2.booleanFieldL2", false);
    assertVariable(workflowInstanceEntity, "objectLevel1.objectLevel2.arrayFieldL2[0]", "1");
    assertVariable(workflowInstanceEntity, "objectLevel1.objectLevel2.arrayFieldL2[1]", "2");
    assertVariable(workflowInstanceEntity, "objectLevel1.objectLevel2.arrayFieldL2[2]", "3");
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayOfObjects[0].stringArrayField", "strArr1");
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayOfObjects[0].intArrayField", 123L);
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayOfObjects[0].booleanArrayField", true);
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayOfObjects[1].stringArrayField", "strArr1");
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayOfObjects[1].intArrayField", 321L);
    assertVariable(workflowInstanceEntity, "objectLevel1.arrayOfObjects[1].booleanArrayField", false);
    assertVariable(workflowInstanceEntity, "stringField", "someString");
    assertVariable(workflowInstanceEntity, "intField", 7L);
    assertVariable(workflowInstanceEntity, "nullField", null);

  }

  private void assertVariable(WorkflowInstanceEntity workflowInstanceEntity, String name, Object value) {
    if (value instanceof String || value == null) {
      assertThat(workflowInstanceEntity.getStringVariables()).filteredOn(v -> v.getName().equals(name)).hasSize(1)
        .first().matches(v -> v.getValue() == null ? value == null : v.getValue().equals(value));
    } else if (value instanceof Long) {
      assertThat(workflowInstanceEntity.getLongVariables()).filteredOn(v -> v.getName().equals(name)).hasSize(1)
        .first().matches(v -> v.getValue().equals(value));
    } else if (value instanceof Double) {
      assertThat(workflowInstanceEntity.getDoubleVariables()).filteredOn(v -> v.getName().equals(name)).hasSize(1)
        .first().matches(v -> v.getValue().equals(value));
    } else if (value instanceof Boolean) {
      assertThat(workflowInstanceEntity.getBooleanVariables()).filteredOn(v -> v.getName().equals(name)).hasSize(1)
        .first().matches(v -> v.getValue().equals(value));
    } else {
      fail("Variable %s must be present in one of the collections", name);
    }
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