package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.function.Predicate;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.DetailViewReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class WorkflowInstanceImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private DetailViewReader detailViewReader;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private WorkflowCache workflowCache;

  @Autowired
  @Qualifier("activityIsTerminatedCheck")
  private Predicate<Object[]> activityIsTerminatedCheck;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("incidentIsResolvedCheck")
  private Predicate<Object[]> incidentIsResolvedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  private Predicate<Object[]> workflowInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  private Predicate<Object[]> workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCreatedCheck")
  private Predicate<Object[]> workflowInstanceIsCreatedCheck;

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
  public void testWorkflowInstanceCreated() {
    // having
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");

    //then
    final String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getWorkflowId()).isEqualTo(workflowId);
    assertThat(wi.getWorkflowName()).isEqualTo("Demo process");
    assertThat(wi.getWorkflowVersion()).isEqualTo(1);
    assertThat(wi.getId()).isEqualTo(workflowInstanceId);
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.ACTIVE);
    assertThat(wi.getEndDate()).isNull();
    assertThat(wi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    assertStartActivityCompleted(tree.getChildren().get(0));
    assertActivityIsActive(tree.getChildren().get(1), "taskA");
  }

  protected ActivityInstanceTreeDto getActivityInstanceTree(long workflowInstanceKey) {
    return detailViewReader.getActivityInstanceTree(new ActivityInstanceTreeRequestDto(IdTestUtil.getId(workflowInstanceKey)));
  }

  protected ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView() {
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(TestUtil.createGetAllWorkflowInstancesQuery(), 0, 100);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(1);
    return listViewResponse.getWorkflowInstances().get(0);
  }

  @Test
  public void testWorkflowInstanceAndActivityCompleted() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeTaskType("task1")
      .endEvent()
      .done();
    deployWorkflow(workflow, "demoProcess_v_1.bpmn");

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");

    completeTask(workflowInstanceKey, "task1", null);

    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.COMPLETED);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(3);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);

  }

  @Test
  public void testWorkflowInstanceStartTimeDoesNotChange() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeTaskType("task1")
      .endEvent()
      .done();
    deployWorkflow(workflow, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");
    //remember start date
    final OffsetDateTime startDate = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey)).getStartDate();

    //when
    completeTask(workflowInstanceKey, "task1", null);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
    //assert start date did not change
    assertThat(workflowInstanceEntity.getStartDate()).isEqualTo(startDate);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getStartDate()).isEqualTo(startDate);

  }

  @Test
  public void testSequenceFlowsPersisted() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .sequenceFlowId("sf1")
      .serviceTask("task1").zeebeTaskType("task1")
      .sequenceFlowId("sf2")
      .serviceTask("task2").zeebeTaskType("task2")
      .sequenceFlowId("sf3")
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");

    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");

    completeTask(workflowInstanceKey, "task1", null);

    completeTask(workflowInstanceKey, "task2", null);

    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getSequenceFlows()).hasSize(3)
      .extracting(WorkflowInstanceTemplate.ACTIVITY_ID).containsOnly("sf1", "sf2", "sf3");

  }

/*
  @Test
  public void testVariablesCreated() throws URISyntaxException, IOException {
    // having
    String payload = new String(Files.readAllBytes(Paths.get(WorkflowInstanceImportIT.class.getResource("/payload.json").toURI())));
    String processId = "demoProcess";
    String workflowId = deployWorkflow("demoProcess_v_1.bpmn");

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, payload);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));

    assertThat(workflowInstanceEntity.countVariables()).isEqualTo(23);

    assertVariable(workflowInstanceEntity, "objectLevel1.stringFieldL1","stringValue1");
    assertVariable(workflowInstanceEntity, "objectLevel1.stringFieldL1", "stringValue1");
    assertVariable(workflowInstanceEntity, "objectLevel1.intFieldL1", 111L);
    assertVariable(workflowInstanceEntity, "objectLevel1.longFieldL1", 2147483648L);
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
*/
  @Test
  public void testIncidentDeleted() {
    // having
    String activityId = "taskA";

    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    failTaskWithNoRetriesLeft(activityId, workflowInstanceKey, "Some error");

    //when update retries
    final String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);
    final WorkflowInstanceEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstance.getIncidents().size()).isEqualTo(1);
    ZeebeTestUtil.resolveIncident(zeebeClient, workflowInstance.getIncidents().get(0).getJobId(), workflowInstance.getIncidents().get(0).getKey());
    elasticsearchTestRule.processAllEvents(19);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.ACTIVE);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVE);
    assertThat(activity.getActivityId()).isEqualTo(activityId);

  }

  @Test
  public void testWorkflowInstanceWithIncidentCreated() {
    // having
    String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";

    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //when
    //create an incident
    failTaskWithNoRetriesLeft(activityId, workflowInstanceKey, errorMessage);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getActivityInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(incidentEntity.getErrorType()).isNotEmpty();
    //TODO OPE-400
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getActivityId()).isEqualTo(activityId);

  }

  @Test
  public void testWorkflowInstanceWithIncidentOnGateway() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("$.foo < 5")
          .serviceTask("task1").zeebeTaskType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("$.foo >= 5")
          .serviceTask("task2").zeebeTaskType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployWorkflow(workflow, resourceName);

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");

    //then incident created, activity in INCIDENT state
    WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getActivityInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotEmpty();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);


    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getActivityId()).isEqualTo(activityId);

    //when payload updated
//TODO    ZeebeUtil.updatePayload(zeebeClient, gatewayActivity.getKey(), workflowInstanceId, "{\"foo\": 7}", processId, workflowId);
//    elasticsearchTestRule.processAllEvents(5);

    //then incident is resolved
//TODO    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
//    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
//    incidentEntity = workflowInstanceEntity.getIncidents().get(0);
//    assertThat(incidentEntity.getElementId()).isEqualTo(activityId);
//    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    //assert activity fields
//TODO    final ActivityInstanceEntity xorActivity = workflowInstanceEntity.getActivities().stream().filter(a -> a.getElementId().equals("xor"))
//      .findFirst().get();
//    assertThat(xorActivity.getState()).isEqualTo(ActivityState.COMPLETED);
//    assertThat(xorActivity.getEndDate()).isNotNull();
  }

  @Test
  public void testWorkflowInstanceWithIncidentOnGatewayIsCanceled() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("$.foo < 5")
          .serviceTask("task1").zeebeTaskType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("$.foo >= 5")
          .serviceTask("task2").zeebeTaskType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployWorkflow(workflow, resourceName);

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllEventsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //then incident created, activity in INCIDENT state
    WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //when I cancel workflow instance
    ZeebeTestUtil.cancelWorkflowInstance(zeebeClient, workflowInstanceKey);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllEventsAndWait(incidentIsResolvedCheck, workflowInstanceKey);

    //then incident is deleted
    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();

  }

  @Test
  public void testWorkflowInstanceGatewayIsPassed() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("$.foo < 5")
          .serviceTask("task1").zeebeTaskType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("$.foo >= 5")
          .serviceTask("task2").zeebeTaskType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployWorkflow(workflow, resourceName);

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"foo\": 6}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren().size()).isGreaterThanOrEqualTo(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();

  }

  @Test
  public void testWorkflowInstanceEventBasedGatewayIsActive() {
    // having
    String activityId = "gateway";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .eventBasedGateway(activityId)
      .intermediateCatchEvent(
        "msg-1", i -> i.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key1")))
      .endEvent()
      .moveToLastGateway()
      .intermediateCatchEvent(
        "msg-2", i -> i.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key2")))
      .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployWorkflow(workflow, resourceName);

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"key1\": \"value1\", \"key2\": \"value2\"}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "gateway");

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVE);
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNull();

  }

  @Test
  public void testWorkflowInstanceCanceled() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");

    //when
    cancelWorkflowInstance(workflowInstanceKey);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceEntity.getEndDate()).isNotNull();
    assertThat(workflowInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getEndDate()).isNotNull();
    assertThat(wi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  @Test
  public void testWorkflowInstanceCanceledOnMessageEvent() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "eventProcess";
    final String workflowId = deployWorkflow("messageEventProcess_v_1.bpmn");
//    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"clientId\": \"5\"}");

        try {
          Thread.sleep(1000L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

    //when
    cancelWorkflowInstance(workflowInstanceKey);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceEntity.getEndDate()).isNotNull();
    assertThat(workflowInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getEndDate()).isNotNull();
    assertThat(wi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final DetailViewActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  @Test
  public void testWorkflowInstanceById() {
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);

    final WorkflowInstanceEntity workflowInstanceById = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceById).isNotNull();
    assertThat(workflowInstanceById.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
  }

  @Test
  public void testWorkflowInstanceWithIncidentById() {
    String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);

    //create an incident
    failTaskWithNoRetriesLeft(activityId, workflowInstanceKey, errorMessage);
    elasticsearchTestRule.processAllEventsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    final WorkflowInstanceEntity workflowInstanceById = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceById).isNotNull();
    assertThat(workflowInstanceById.getState()).isEqualTo(WorkflowInstanceState.INCIDENT);
  }

  @Test(expected = NotFoundException.class)
  public void testWorkflowInstanceByIdFailForUnknownId() {
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);

    final WorkflowInstanceEntity workflowInstanceById = workflowInstanceReader.getWorkflowInstanceById("wrongId");
  }

  private void assertStartActivityCompleted(DetailViewActivityInstanceDto startActivity) {
    assertThat(startActivity.getActivityId()).isEqualTo("start");
    assertThat(startActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(startActivity.getType()).isEqualTo(ActivityType.START_EVENT);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsActive(DetailViewActivityInstanceDto activity, String activityId) {
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
  }

}