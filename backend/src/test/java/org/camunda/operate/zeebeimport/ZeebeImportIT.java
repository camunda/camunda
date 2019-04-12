/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.client.ZeebeClient;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ZeebeImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private WorkflowCache workflowCache;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  private ZeebeClient zeebeClient;

  private OffsetDateTime testStartTime;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Before
  public void init() {
    super.before();
    testStartTime = OffsetDateTime.now();
    zeebeClient = super.getClient();
    mockMvc = mockMvcTestRule.getMockMvc();
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
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load workflow instance index, then deployment
    processAllEvents(10, ImportValueType.WORKFLOW_INSTANCE);
    processAllEvents(2, ImportValueType.DEPLOYMENT);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
  }

  protected void processAllEvents(int expectedMinEventsCount, ImportValueType workflowInstance) {
    elasticsearchTestRule.processAllEvents(expectedMinEventsCount, workflowInstance);
  }

  @Test
  public void testIncidentCreatesWorkflowInstance() {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load incident and then workflow instance events
    processAllEvents(1, ImportValueType.INCIDENT);
    processAllEvents(8, ImportValueType.WORKFLOW_INSTANCE);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.INCIDENT);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(IdTestUtil.getId(workflowInstanceKey));
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);
    assertThat(wi.getWorkflowId()).isEqualTo(workflowId);
    assertThat(wi.getWorkflowName()).isEqualTo("Demo process");
    assertThat(wi.getWorkflowVersion()).isEqualTo(1);
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getEndDate()).isNull();
    assertThat(wi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    assertStartActivityCompleted(tree.getChildren().get(0));
    assertActivityIsInIncidentState(tree.getChildren().get(1), "taskA");
  }

  protected ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView() {
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(TestUtil.createGetAllWorkflowInstancesQuery(), 0, 100);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(1);
    return listViewResponse.getWorkflowInstances().get(0);
  }


  protected ActivityInstanceTreeDto getActivityInstanceTree(long workflowInstanceKey) {
    return activityInstanceReader.getActivityInstanceTree(new ActivityInstanceTreeRequestDto(IdTestUtil.getId(workflowInstanceKey)));
  }


  @Test
  public void testOnlyIncidentIsLoaded() throws Exception {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //load only incidents
    processAllEvents(1, ImportValueType.INCIDENT);

    assertListViewResponse();
    //if nothing is returned in list view - there is no way to access the workflow instance, no need to check other queries

  }

  protected void assertListViewResponse() throws Exception {
    ListViewRequestDto listViewRequest = TestUtil.createGetAllWorkflowInstancesQuery();
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(listViewRequest))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //check that nothing is returned
    final ListViewResponseDto listViewResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(0);
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
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, IdTestUtil.getId(jobKey), incidentKey);

    setJobWorker(ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}"));

    processAllEvents(20, ImportValueType.WORKFLOW_INSTANCE);
    processAllEvents(2, ImportValueType.INCIDENT);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(IdTestUtil.getId(workflowInstanceKey));
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);
    assertThat(wi.getEndDate()).isNotNull();

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren().size()).isGreaterThanOrEqualTo(2);
    assertStartActivityCompleted(tree.getChildren().get(0));
    assertActivityIsCompleted(tree.getChildren().get(1), "taskA");

  }

  protected long getOnlyIncidentKey() {
    final List<Record<IncidentRecordValue>> incidents = RecordingExporter.incidentRecords(IncidentIntent.CREATED)
      .collect(Collectors.toList());
    assertThat(incidents).hasSize(1);
    return incidents.get(0).getKey();
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
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, IdTestUtil.getId(jobKey), incidentKey);

    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceKey);

    processAllEvents(20, ImportValueType.WORKFLOW_INSTANCE);
    processAllEvents(2, ImportValueType.INCIDENT);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(IdTestUtil.getId(workflowInstanceKey));
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getEndDate()).isNotNull();

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren().size()).isGreaterThanOrEqualTo(2);
    final ActivityInstanceDto activityInstance = tree.getChildren().get(1);
    assertThat(activityInstance.getActivityId()).isEqualTo(activityId);
    assertThat(activityInstance.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activityInstance.getEndDate()).isNotNull();

  }

  @Test
  public void testPartitionIds() {
    final Set<Integer> operatePartitions = zeebeESImporter.getPartitionIds();
    final int zeebePartitionsCount = zeebeClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id < zeebePartitionsCount && id >= 0);
  }

  private void assertStartActivityCompleted(ActivityInstanceDto activity) {
    assertActivityIsCompleted(activity, "start");
  }

  private void assertActivityIsInIncidentState(ActivityInstanceDto activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsCompleted(ActivityInstanceDto activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isAfterOrEqualTo(activity.getStartDate());
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", WORKFLOW_INSTANCE_URL, firstResult, maxResults);
  }

}