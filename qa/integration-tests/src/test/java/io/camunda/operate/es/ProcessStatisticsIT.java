/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstanceWithIncident;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.qa.util.RestAPITestUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests Elasticsearch query for process statistics.
 */
public class ProcessStatisticsIT extends OperateIntegrationTest {

  private static final String QUERY_PROCESS_STATISTICS_URL = "/api/process-instances/statistics";
  private static final String QUERY_PROCESS_CORE_STATISTICS_URL = "/api/process-instances/core-statistics";

  private static final Long PROCESS_KEY_DEMO_PROCESS = 42L;
  private static final Long PROCESS_KEY_OTHER_PROCESS = 27L;

  @MockBean
  private PermissionsService permissionsService;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testOneProcessStatistics() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS));
  }

  @Test
  public void testStatisticsWithQueryByActivityId() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS);
    queryRequest.setActivityId("taskA");

    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
  }

  @Test
  public void testStatisticsWithQueryByErrorMessage() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS);
    queryRequest.setErrorMessage("error");

    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(2);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskC")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(2L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskE")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );
  }

  @Test
  public void testFailStatisticsWithNoProcessId() throws Exception {
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, query);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithBpmnProcessIdButNoVersion() throws Exception {

    String bpmnProcessId = "demoProcess";

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery();
    queryRequest.setBpmnProcessId(bpmnProcessId);

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneProcessDefinitionKey() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    final ListViewQueryDto query = createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS, PROCESS_KEY_OTHER_PROCESS);

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, query);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one process must be specified in the request");
  }


  @Test
  public void testFailStatisticsWithProcessDefinitionKeyAndBpmnProcessId() throws Exception {
    Long processDefinitionKey = 1L;
    String bpmnProcessId = "demoProcess";
    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);
    queryRequest
      .setBpmnProcessId(bpmnProcessId)
      .setProcessVersion(1);

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL,queryRequest);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testTwoProcessesStatistics() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);
    createData(PROCESS_KEY_OTHER_PROCESS);

    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS));
    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_OTHER_PROCESS));
  }

  @Test
  public void testGetCoreStatistics() throws Exception {
    // when request core-statistics
    ProcessInstanceCoreStatisticsDto coreStatistics = mockMvcTestRule.fromResponse( getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);
    // then return zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 0L);
    assertEquals(coreStatistics.getRunning().longValue(), 0L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 0L);

    // given test data
    createData(PROCESS_KEY_DEMO_PROCESS);
    createData(PROCESS_KEY_OTHER_PROCESS);

    // when request core-statistics
    coreStatistics = mockMvcTestRule.fromResponse(getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);
    // then return non-zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 6L);
    assertEquals(coreStatistics.getRunning().longValue(), 12L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 6L);
  }

  @Test
  public void testStatisticsWithPermisssionWhenAllowed() throws Exception {

    // given
    Long processDefinitionKey = PROCESS_KEY_DEMO_PROCESS;

    List<OperateEntity> entities = new ArrayList<>();
    ProcessInstanceForListViewEntity processInstance = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(TestUtil.createFlowNodeInstance(processInstance.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil.createFlowNodeInstance(processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(TestUtil.createFlowNodeInstance(processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskB", null));
    entities.add(processInstance);
    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

    ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of(processInstance.getBpmnProcessId())));
    when(permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    // then
    Collection<FlowNodeStatisticsDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.size()).isEqualTo(2);
  }

  @Test
  public void testStatisticsWithPermisssionWhenNotAllowed() throws Exception {

    // given
    Long processDefinitionKey = PROCESS_KEY_DEMO_PROCESS;

    List<OperateEntity> entities = new ArrayList<>();
    ProcessInstanceForListViewEntity processInstance = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(TestUtil.createFlowNodeInstance(processInstance.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil.createFlowNodeInstance(processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(TestUtil.createFlowNodeInstance(processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskB", null));
    entities.add(processInstance);
    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

    ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    when(permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    // then
    Collection<FlowNodeStatisticsDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).isEmpty();
  }

  @Test
  public void testCoreStatisticsWithPermisssionWhenAllowed() throws Exception {
    // given
    String bpmnProcessId1 = "bpmnProcessId1";
    String bpmnProcessId2 = "bpmnProcessId2";
    String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    elasticsearchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    ListViewRequestDto queryRequest = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(PermissionsService.ResourcesAllowed.all());
    when(permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ)).thenCallRealMethod();

    // then
    ProcessInstanceCoreStatisticsDto coreStatistics = mockMvcTestRule.fromResponse(getRequest(QUERY_PROCESS_CORE_STATISTICS_URL),
        ProcessInstanceCoreStatisticsDto.class);

    assertThat(coreStatistics.getActive()).isEqualTo(3);
  }

  @Test
  public void testCoreStatisticsWithPermisssionWhenNotAllowed() throws Exception {
    // given
    String bpmnProcessId1 = "bpmnProcessId1";
    String bpmnProcessId2 = "bpmnProcessId2";
    String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    elasticsearchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    ListViewRequestDto queryRequest = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ)).thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    when(permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ)).thenCallRealMethod();

    // then
    ProcessInstanceCoreStatisticsDto coreStatistics = mockMvcTestRule.fromResponse(getRequest(QUERY_PROCESS_CORE_STATISTICS_URL),
        ProcessInstanceCoreStatisticsDto.class);

    assertThat(coreStatistics.getActive()).isEqualTo(0);
  }

  private ListViewQueryDto createGetAllProcessInstancesQuery(Long... processDefinitionKeys) {
    ListViewQueryDto q = RestAPITestUtil.createGetAllProcessInstancesQuery();
    if (processDefinitionKeys != null && processDefinitionKeys.length > 0) {
      q.setProcessIds(CollectionUtil.toSafeListOfStrings(processDefinitionKeys));
    }
    return q;
  }

  private void getStatisticsAndAssert(ListViewQueryDto query) throws Exception {
    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);

    assertThat(activityStatisticsDtos).hasSize(5);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
        ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskC")).allMatch(ai->
        ai.getActive().equals(0L) && ai.getCanceled().equals(2L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(2L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskD")).allMatch(ai->
        ai.getActive().equals(0L) && ai.getCanceled().equals(1L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskE")).allMatch(ai->
        ai.getActive().equals(1L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("end")).allMatch(ai->
        ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(2L) && ai.getIncidents().equals(0L)
    );
  }

  private List<FlowNodeStatisticsDto> getActivityStatistics(ListViewQueryDto query) throws Exception {
    return mockMvcTestRule.listFromResponse(postRequest(QUERY_PROCESS_STATISTICS_URL, query), FlowNodeStatisticsDto.class);
  }

  /**
   * start
   * taskA  - 2 active
   * taskB
   * taskC  -           - 2 canceled  - 2 with incident
   * taskD  -           - 1 canceled
   * taskE  - 1 active  -             - 1 with incident
   * end    -           -             -                   - 2 finished
   */
  protected void createData(Long processDefinitionKey) {

    List<OperateEntity> entities = new ArrayList<>();

    ProcessInstanceForListViewEntity inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));    //duplicated on purpose, to be sure, that we count process instances, but not activity instances
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    String error = "error";
    FlowNodeInstanceForListViewEntity task = createFlowNodeInstanceWithIncident(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    task = createFlowNodeInstanceWithIncident(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskD", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskE", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    task = createFlowNodeInstanceWithIncident(inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskE");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.COMPLETED, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.COMPLETED, processDefinitionKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

  }
}
