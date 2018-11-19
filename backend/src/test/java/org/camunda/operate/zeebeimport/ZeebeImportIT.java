package org.camunda.operate.zeebeimport;

import java.time.OffsetDateTime;
import java.util.Set;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import com.sun.xml.internal.ws.spi.db.FieldGetter;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ZeebeImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private WorkflowCache workflowCache;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  private ZeebeClient zeebeClient;

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    super.before();
    testStartTime = OffsetDateTime.now();
    zeebeClient = super.getClient();
    try {
      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("zeebeClient"), super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @After
  public void after() {
    super.after();
  }

  @Test
  public void testWorkflowNameAndVersionAreLoaded() {
    // having
    String processId = "demoProcess";
    final String workflowId = ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load workflow instance index, then deployment
    elasticsearchTestRule.processAllEvents(10, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);
    elasticsearchTestRule.processAllEvents(2, ZeebeESImporter.ImportValueType.DEPLOYMENT);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
  }

  @Test
  public void testIncidentCreatesWorkflowInstance() {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final String workflowInstanceId = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load incident and then workflow instance events
    elasticsearchTestRule.processAllEvents(1, ZeebeESImporter.ImportValueType.INCIDENT);
    elasticsearchTestRule.processAllEvents(8, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

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
  public void testIncidentDeletedAfterActivityCompleted() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
      .done();
    final String workflowId = deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3);

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey);

    setJobWorker(ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}"));

    elasticsearchTestRule.processAllEvents(20, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);
    elasticsearchTestRule.processAllEvents(2, ZeebeESImporter.ImportValueType.INCIDENT);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.DELETED);
    assertThat(workflowInstanceEntity.getActivities()).filteredOn(ai -> ai.getId().equals(incidentEntity.getActivityInstanceId()))
      .hasSize(1)
      .extracting(WorkflowInstanceType.STATE).containsOnly(ActivityState.COMPLETED);

  }

  @Test
  public void testIncidentDeletedAfterActivityTerminated() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
        .done();
    final String workflowId = deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3);

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey);

    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceId);

    elasticsearchTestRule.processAllEvents(20, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);
    elasticsearchTestRule.processAllEvents(2, ZeebeESImporter.ImportValueType.INCIDENT);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.DELETED);
    assertThat(workflowInstanceEntity.getActivities()).filteredOn(ai -> ai.getId().equals(incidentEntity.getActivityInstanceId()))
      .hasSize(1)
      .extracting(WorkflowInstanceType.STATE).containsOnly(ActivityState.TERMINATED);

  }

  @Test
  public void testPartitionIds() {
    final Set<Integer> operatePartitions = zeebeESImporter.getPartitionIds();
    final int zeebePartitionsCount = zeebeClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id < zeebePartitionsCount && id >= 0);
  }

  private void assertStartActivityCompleted(ActivityInstanceEntity startActivity) {
    assertThat(startActivity.getActivityId()).isEqualTo("start");
    assertThat(startActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsInIncidentState(ActivityInstanceEntity activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

}