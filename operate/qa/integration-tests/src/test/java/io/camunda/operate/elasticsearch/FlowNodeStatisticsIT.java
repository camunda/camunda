/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstanceWithIncident;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.qa.util.RestAPITestUtil;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch query for process statistics. */
public class FlowNodeStatisticsIT extends OperateAbstractIT {

  private static final String QUERY_PROCESS_STATISTICS_URL = "/api/process-instances/statistics";
  private static final String QUERY_PROCESS_CORE_STATISTICS_URL =
      "/api/process-instances/core-statistics";

  private static final Long PROCESS_KEY_DEMO_PROCESS = 42L;
  private static final Long PROCESS_KEY_OTHER_PROCESS = 27L;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testOneProcessStatistics() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS));
  }

  @Test
  public void testStatisticsWithQueryByActivityId() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    final ListViewQueryDto queryRequest =
        createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS);
    queryRequest.setActivityId("taskA");

    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskA"))
        .allMatch(
            ai ->
                ai.getActive().equals(2L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(0L));
  }

  @Test
  public void testStatisticsWithQueryByErrorMessage() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    final ListViewQueryDto queryRequest =
        createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS);
    queryRequest.setErrorMessage("error");

    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(2);
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskC"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(2L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskE"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(1L));
  }

  @Test
  public void testTwoProcessesStatistics() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);
    createData(PROCESS_KEY_OTHER_PROCESS);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS));
    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_OTHER_PROCESS));
  }

  @Test
  public void testGetCoreStatistics() throws Exception {
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    // when request core-statistics
    ProcessInstanceCoreStatisticsDto coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);
    // then return zero statistics
    assertThat(coreStatistics.getActive().longValue()).isEqualTo(0L);
    assertThat(coreStatistics.getRunning().longValue()).isEqualTo(0L);
    assertThat(coreStatistics.getWithIncidents().longValue()).isEqualTo(0L);

    // given test data
    createData(PROCESS_KEY_DEMO_PROCESS);
    createData(PROCESS_KEY_OTHER_PROCESS);

    // when request core-statistics
    coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);
    // then return non-zero statistics
    assertThat(coreStatistics.getActive().longValue()).isEqualTo(6L);
    assertThat(coreStatistics.getRunning().longValue()).isEqualTo(12L);
    assertThat(coreStatistics.getWithIncidents().longValue()).isEqualTo(6L);
  }

  @Test
  public void testStatisticsWithPermissionWhenNotAllowed() throws Exception {

    // given
    final Long processDefinitionKey = PROCESS_KEY_DEMO_PROCESS;

    final List<ExporterEntity> entities = new ArrayList<>();
    final ProcessInstanceForListViewEntity processInstance =
        createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskB", null));
    entities.add(processInstance);
    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));

    final MvcResult mvcResult = postRequest(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    // then
    final Collection<FlowNodeStatisticsDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).isEmpty();
  }

  @Test
  public void testCoreStatisticsWithPermissionWhenNotAllowed() throws Exception {
    // given
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto queryRequest = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));

    // then
    final ProcessInstanceCoreStatisticsDto coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);

    assertThat(coreStatistics.getActive()).isEqualTo(0);
  }

  private ListViewQueryDto createGetAllProcessInstancesQuery(final Long... processDefinitionKeys) {
    final ListViewQueryDto q = RestAPITestUtil.createGetAllProcessInstancesQuery();
    if (processDefinitionKeys != null && processDefinitionKeys.length > 0) {
      q.setProcessIds(CollectionUtil.toSafeListOfStrings(processDefinitionKeys));
    }
    return q;
  }

  private void getStatisticsAndAssert(final ListViewQueryDto query) throws Exception {
    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);

    assertThat(activityStatisticsDtos).hasSize(5);
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskA"))
        .allMatch(
            ai ->
                ai.getActive().equals(2L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(0L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskC"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(2L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(2L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskD"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(1L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(0L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskE"))
        .allMatch(
            ai ->
                ai.getActive().equals(1L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(1L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("end"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(2L)
                    && ai.getIncidents().equals(0L));
  }

  private List<FlowNodeStatisticsDto> getActivityStatistics(final ListViewQueryDto query)
      throws Exception {
    return mockMvcTestRule.listFromResponse(
        postRequest(QUERY_PROCESS_STATISTICS_URL, query), FlowNodeStatisticsDto.class);
  }

  /**
   * start taskA - 2 active taskB taskC - - 2 canceled - 2 with incident taskD - - 1 canceled taskE
   * - 1 active - - 1 with incident end - - - - 2 finished
   */
  protected void createData(final Long processDefinitionKey) {

    final List<ExporterEntity> entities = new ArrayList<>();

    ProcessInstanceForListViewEntity inst =
        createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(),
            FlowNodeState.ACTIVE,
            "taskA",
            null)); // duplicated on purpose, to be sure, that we count process instances, but not
    // activity instances
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    final String error = "error";
    FlowNodeInstanceForListViewEntity task =
        createFlowNodeInstanceWithIncident(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    task =
        createFlowNodeInstanceWithIncident(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskD", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskE", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    task =
        createFlowNodeInstanceWithIncident(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskE");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.COMPLETED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.COMPLETED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }
}
