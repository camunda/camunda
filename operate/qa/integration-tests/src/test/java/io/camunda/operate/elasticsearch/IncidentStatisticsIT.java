/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.operate.util.TestUtil.createProcessInstanceEntity;
import static io.camunda.operate.util.TestUtil.createProcessVersions;
import static io.camunda.operate.webapp.rest.IncidentRestService.INCIDENT_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class IncidentStatisticsIT extends OperateAbstractIT {

  public static final String LOAN_BPMN_PROCESS_ID = "loanProcess";
  public static final String LOAN_PROCESS_NAME = "Loan process";
  public static final String DEMO_BPMN_PROCESS_ID = "demoProcess";
  public static final String DEMO_PROCESS_NAME = "Demo process";
  public static final String ORDER_BPMN_PROCESS_ID = "orderProcess";
  public static final String ORDER_PROCESS_NAME = "Order process";
  public static final String NO_INSTANCES_PROCESS_ID = "noInstancesProcess";
  public static final String NO_INSTANCES_PROCESS_NAME = "No Instances Process";
  public static final String ERRMSG_OTHER = "Other error message";
  public static final Integer ERRMSG_OTHER_HASH_CODE = ERRMSG_OTHER.hashCode();
  private static final String QUERY_INCIDENTS_BY_PROCESS_URL = INCIDENT_URL + "/byProcess";
  private static final String QUERY_INCIDENTS_BY_ERROR_URL = INCIDENT_URL + "/byError";
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockitoBean private PermissionsService permissionsService;
  private final Random random = new Random();

  private final String tenantId1 = "tenant1";
  private final String tenantId2 = "tenant2";

  @Test
  public void testAbsentProcessDoesntThrowExceptions() throws Exception {
    final List<ExporterEntity> entities = new ArrayList<>();
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    // Create a processInstance that has no matching process
    final Long processDefinitionKey = 0L;
    final ProcessInstanceForListViewEntity processInstance =
        createProcessInstanceEntity(ProcessInstanceState.ACTIVE, processDefinitionKey, "process");
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 1, 0));
    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));

    final List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();

    assertThat(response).hasSize(1);
  }

  @Test
  public void testIncidentStatisticsByError() throws Exception {
    createData();
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    final List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();
    assertThat(response).hasSize(2);

    // assert NO_RETRIES_LEFT
    IncidentsByErrorMsgStatisticsDto incidentsByErrorStat = response.get(0);
    assertThat(incidentsByErrorStat.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(incidentsByErrorStat.getInstancesWithErrorCount()).isEqualTo(3L);
    assertThat(incidentsByErrorStat.getProcesses()).hasSize(2);

    final Iterator<IncidentByProcessStatisticsDto> iterator =
        incidentsByErrorStat.getProcesses().iterator();
    IncidentByProcessStatisticsDto next = iterator.next();
    assertThat(next.getName()).isEqualTo(DEMO_PROCESS_NAME + 1);
    assertThat(next.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(next.getTenantId()).isEqualTo(tenantId1);
    assertThat(next.getInstancesWithActiveIncidentsCount()).isEqualTo(2L);
    assertThat(next.getActiveInstancesCount()).isEqualTo(0);
    assertThat(next.getVersion()).isEqualTo(1);
    assertThat(next.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(next.getProcessId()).isNotNull();

    next = iterator.next();
    assertThat(next.getName()).isEqualTo(ORDER_PROCESS_NAME + 2);
    assertThat(next.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(next.getTenantId()).isEqualTo(tenantId2);
    assertThat(next.getInstancesWithActiveIncidentsCount()).isEqualTo(1L);
    assertThat(next.getActiveInstancesCount()).isEqualTo(0);
    assertThat(next.getVersion()).isEqualTo(2);
    assertThat(next.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(next.getProcessId()).isNotNull();

    // assert OTHER_ERRMSG
    incidentsByErrorStat = response.get(1);
    assertThat(incidentsByErrorStat.getErrorMessage()).isEqualTo(ERRMSG_OTHER);
    assertThat(incidentsByErrorStat.getInstancesWithErrorCount()).isEqualTo(2L);
    assertThat(incidentsByErrorStat.getProcesses()).hasSize(2);
    assertThat(incidentsByErrorStat.getProcesses())
        .allMatch(
            s ->
                s.getProcessId() != null
                    && s.getName().equals(DEMO_PROCESS_NAME + s.getVersion())
                    && s.getErrorMessage().equals(ERRMSG_OTHER)
                    && s.getTenantId().equals(tenantId1)
                    && s.getInstancesWithActiveIncidentsCount() == 1L
                    && (s.getVersion() == 1 || s.getVersion() == 2));
  }

  @Test
  public void testProcessAndIncidentStatistics() throws Exception {
    createData();
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    final List<IncidentsByProcessGroupStatisticsDto> processGroups = requestIncidentsByProcess();

    assertThat(processGroups).hasSize(3);
    assertDemoProcess(processGroups.get(0));
    assertOrderProcess(processGroups.get(1));
    assertLoanProcess(processGroups.get(2));
  }

  @Test
  public void testProcessWithoutInstancesIsSortedByVersionAscending() throws Exception {
    createNoInstancesProcessData(3);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    final List<IncidentsByProcessGroupStatisticsDto> processGroups = requestIncidentsByProcess();

    assertThat(processGroups).hasSize(1);
    final Collection<IncidentByProcessStatisticsDto> processes =
        processGroups.get(0).getProcesses();
    assertThat(processes).hasSize(3);

    final Iterator<IncidentByProcessStatisticsDto> processIterator = processes.iterator();
    assertNoInstancesProcess(processIterator.next(), 1);
    assertNoInstancesProcess(processIterator.next(), 2);
    assertNoInstancesProcess(processIterator.next(), 3);
  }

  @Test
  public void testIncidentsByProcessWithPermissionWhenAllowed() throws Exception {

    // given
    createData();

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    // then
    final List<IncidentsByProcessGroupStatisticsDto> response = requestIncidentsByProcess();

    assertThat(response).hasSize(3);
    assertThat(
            response.stream()
                .map(IncidentsByProcessGroupStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            DEMO_BPMN_PROCESS_ID, ORDER_BPMN_PROCESS_ID, LOAN_BPMN_PROCESS_ID);
  }

  @Test
  public void testIncidentsByProcessWithPermissionWhenNotAllowed() throws Exception {

    // given
    createData();

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(
            PermissionsService.ResourcesAllowed.withIds(
                Set.of(DEMO_BPMN_PROCESS_ID, ORDER_BPMN_PROCESS_ID)));

    // then
    final List<IncidentsByProcessGroupStatisticsDto> response = requestIncidentsByProcess();

    assertThat(response).hasSize(2);
    assertThat(
            response.stream()
                .map(IncidentsByProcessGroupStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(DEMO_BPMN_PROCESS_ID, ORDER_BPMN_PROCESS_ID);
  }

  @Test
  public void testIncidentsByErrorWithPermisssionWhenAllowed() throws Exception {

    // given
    createData();

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

    // then
    final List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();

    assertThat(response).hasSize(2);

    final IncidentsByErrorMsgStatisticsDto incidentsByError1 =
        response.stream()
            .filter(x -> Objects.equals(x.getErrorMessage(), TestUtil.ERROR_MSG))
            .findFirst()
            .orElseThrow();
    assertThat(incidentsByError1.getInstancesWithErrorCount()).isEqualTo(3);
    assertThat(incidentsByError1.getProcesses()).hasSize(2);
    assertThat(incidentsByError1.getIncidentErrorHashCode())
        .isEqualTo(TestUtil.ERROR_MSG_HASH_CODE);
    assertThat(
            incidentsByError1.getProcesses().stream()
                .map(IncidentByProcessStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(DEMO_BPMN_PROCESS_ID, ORDER_BPMN_PROCESS_ID);

    final IncidentsByErrorMsgStatisticsDto incidentsByError2 =
        response.stream()
            .filter(x -> Objects.equals(x.getErrorMessage(), ERRMSG_OTHER))
            .findFirst()
            .orElseThrow();
    assertThat(incidentsByError2.getInstancesWithErrorCount()).isEqualTo(2);
    assertThat(incidentsByError2.getProcesses()).hasSize(2);
    assertThat(incidentsByError2.getIncidentErrorHashCode()).isEqualTo(ERRMSG_OTHER_HASH_CODE);
    assertThat(
            incidentsByError2.getProcesses().stream()
                .map(IncidentByProcessStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(DEMO_BPMN_PROCESS_ID, DEMO_BPMN_PROCESS_ID);
  }

  @Test
  public void testIncidentsByErrorWithPermisssionWhenNotAllowed() throws Exception {

    // given
    createData();

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));

    // then
    final List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();

    assertThat(response).isEmpty();
  }

  @Test
  public void testIncidentsByErrorWithPermisssionWhenOnlyDemoProcessAllowed() throws Exception {

    // given
    createData();

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(DEMO_BPMN_PROCESS_ID)));
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(DEMO_BPMN_PROCESS_ID)));

    // then
    final List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();

    assertThat(response).hasSize(2);

    final IncidentsByErrorMsgStatisticsDto incidentsByError1 =
        response.stream()
            .filter(x -> Objects.equals(x.getErrorMessage(), TestUtil.ERROR_MSG))
            .findFirst()
            .orElseThrow();
    assertThat(incidentsByError1.getInstancesWithErrorCount()).isEqualTo(2);
    assertThat(incidentsByError1.getProcesses()).hasSize(1);
    assertThat(incidentsByError1.getIncidentErrorHashCode())
        .isEqualTo(TestUtil.ERROR_MSG_HASH_CODE);
    assertThat(
            incidentsByError1.getProcesses().stream()
                .map(IncidentByProcessStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactly(DEMO_BPMN_PROCESS_ID);

    final IncidentsByErrorMsgStatisticsDto incidentsByError2 =
        response.stream()
            .filter(x -> Objects.equals(x.getErrorMessage(), ERRMSG_OTHER))
            .findFirst()
            .orElseThrow();
    assertThat(incidentsByError2.getInstancesWithErrorCount()).isEqualTo(2);
    assertThat(incidentsByError2.getProcesses()).hasSize(2);
    assertThat(incidentsByError2.getIncidentErrorHashCode()).isEqualTo(ERRMSG_OTHER_HASH_CODE);
    assertThat(
            incidentsByError2.getProcesses().stream()
                .map(IncidentByProcessStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(DEMO_BPMN_PROCESS_ID, DEMO_BPMN_PROCESS_ID);
  }

  @Test
  public void testIncidentsByErrorWithPermisssionWhenOnlyOrderProcessAllowed() throws Exception {

    // given
    createData();

    // when
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(ORDER_BPMN_PROCESS_ID)));
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(ORDER_BPMN_PROCESS_ID)));

    // then
    final List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();

    assertThat(response).hasSize(1);

    final IncidentsByErrorMsgStatisticsDto incidentsByError1 =
        response.stream()
            .filter(x -> Objects.equals(x.getErrorMessage(), TestUtil.ERROR_MSG))
            .findFirst()
            .orElseThrow();
    assertThat(incidentsByError1.getInstancesWithErrorCount()).isEqualTo(1);
    assertThat(incidentsByError1.getProcesses()).hasSize(1);
    assertThat(incidentsByError1.getIncidentErrorHashCode())
        .isEqualTo(TestUtil.ERROR_MSG_HASH_CODE);
    assertThat(
            incidentsByError1.getProcesses().stream()
                .map(IncidentByProcessStatisticsDto::getBpmnProcessId)
                .collect(Collectors.toList()))
        .containsExactly(ORDER_BPMN_PROCESS_ID);
  }

  private void assertNoInstancesProcess(
      final IncidentByProcessStatisticsDto process, final int version) {
    assertThat(process.getVersion()).isEqualTo(version);
    assertThat(process.getActiveInstancesCount()).isEqualTo(0);
    assertThat(process.getInstancesWithActiveIncidentsCount()).isEqualTo(0);
    assertThat(process.getBpmnProcessId()).isEqualTo(NO_INSTANCES_PROCESS_ID);
    assertThat(process.getTenantId()).isEqualTo(tenantId2);
    assertThat(process.getName()).isEqualTo(NO_INSTANCES_PROCESS_NAME + version);
  }

  private void assertLoanProcess(final IncidentsByProcessGroupStatisticsDto loanProcessGroup) {
    assertThat(loanProcessGroup.getBpmnProcessId()).isEqualTo(LOAN_BPMN_PROCESS_ID);
    assertThat(loanProcessGroup.getTenantId()).isEqualTo(tenantId1);
    assertThat(loanProcessGroup.getProcessName()).isEqualTo(LOAN_PROCESS_NAME + "1");
    assertThat(loanProcessGroup.getActiveInstancesCount()).isEqualTo(5);
    assertThat(loanProcessGroup.getInstancesWithActiveIncidentsCount()).isEqualTo(0);
    assertThat(loanProcessGroup.getProcesses()).hasSize(1);

    // assert Loan process version 1
    assertThat(loanProcessGroup.getProcesses()).hasSize(1);
    final IncidentByProcessStatisticsDto loanProcessProcessStatistic =
        loanProcessGroup.getProcesses().iterator().next();
    assertThat(loanProcessProcessStatistic.getName()).isEqualTo(LOAN_PROCESS_NAME + "1");
    assertThat(loanProcessProcessStatistic.getBpmnProcessId()).isEqualTo(LOAN_BPMN_PROCESS_ID);
    assertThat(loanProcessProcessStatistic.getTenantId()).isEqualTo(tenantId1);
    assertThat(loanProcessProcessStatistic.getVersion()).isEqualTo(1);
    assertThat(loanProcessProcessStatistic.getActiveInstancesCount()).isEqualTo(5);
    assertThat(loanProcessProcessStatistic.getInstancesWithActiveIncidentsCount()).isEqualTo(0);
  }

  private void assertOrderProcess(final IncidentsByProcessGroupStatisticsDto orderProcessGroup) {
    // assert Order process group
    assertThat(orderProcessGroup.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(orderProcessGroup.getTenantId()).isEqualTo(tenantId2);
    assertThat(orderProcessGroup.getProcessName()).isEqualTo(ORDER_PROCESS_NAME + "2");
    assertThat(orderProcessGroup.getActiveInstancesCount()).isEqualTo(8);
    assertThat(orderProcessGroup.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
    assertThat(orderProcessGroup.getProcesses()).hasSize(2);
    // assert Order process version 2
    final IncidentByProcessStatisticsDto orderProcess =
        orderProcessGroup.getProcesses().iterator().next();
    assertThat(orderProcess.getName()).isEqualTo(ORDER_PROCESS_NAME + "2");
    assertThat(orderProcess.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(orderProcess.getTenantId()).isEqualTo(tenantId2);
    assertThat(orderProcess.getVersion()).isEqualTo(2);
    assertThat(orderProcess.getActiveInstancesCount()).isEqualTo(3);
    assertThat(orderProcess.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
  }

  private void assertDemoProcess(final IncidentsByProcessGroupStatisticsDto demoProcessGroup) {
    // assert Demo process group
    assertThat(demoProcessGroup.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(demoProcessGroup.getTenantId()).isEqualTo(tenantId1);
    assertThat(demoProcessGroup.getProcessName()).isEqualTo(DEMO_PROCESS_NAME + "2");
    assertThat(demoProcessGroup.getActiveInstancesCount()).isEqualTo(9);
    assertThat(demoProcessGroup.getInstancesWithActiveIncidentsCount()).isEqualTo(4);
    assertThat(demoProcessGroup.getProcesses()).hasSize(2);
    // assert Demo process version 1
    final Iterator<IncidentByProcessStatisticsDto> processes =
        demoProcessGroup.getProcesses().iterator();
    final IncidentByProcessStatisticsDto process1 = processes.next();
    assertThat(process1.getName()).isEqualTo(DEMO_PROCESS_NAME + "1");
    assertThat(process1.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(process1.getTenantId()).isEqualTo(tenantId1);
    assertThat(process1.getVersion()).isEqualTo(1);
    assertThat(process1.getActiveInstancesCount()).isEqualTo(3);
    assertThat(process1.getInstancesWithActiveIncidentsCount()).isEqualTo(3);
    // assert Demo process version 2
    final IncidentByProcessStatisticsDto process2 = processes.next();
    assertThat(process2.getName()).isEqualTo(DEMO_PROCESS_NAME + "2");
    assertThat(process2.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(process2.getTenantId()).isEqualTo(tenantId1);
    assertThat(process2.getVersion()).isEqualTo(2);
    assertThat(process2.getActiveInstancesCount()).isEqualTo(6);
    assertThat(process2.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
  }

  private void createDemoProcessData() {
    final List<ProcessEntity> processVersions =
        createProcessVersions(DEMO_BPMN_PROCESS_ID, DEMO_PROCESS_NAME, 2, tenantId1);
    searchTestRule.persistNew(processVersions.toArray(new ExporterEntity[processVersions.size()]));

    final List<ExporterEntity> entities = new ArrayList<>();

    // Demo process v1
    Long processDefinitionKey = processVersions.get(0).getKey();
    // instance #1
    ProcessInstanceForListViewEntity processInstance =
        createProcessInstanceEntity(
            ProcessInstanceState.ACTIVE, processDefinitionKey, DEMO_BPMN_PROCESS_ID, true);
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 1, 1));
    // instance #2
    processInstance =
        createProcessInstanceEntity(
            ProcessInstanceState.ACTIVE, processDefinitionKey, DEMO_BPMN_PROCESS_ID, true);
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 1, 1, true));
    // instance #3
    processInstance =
        createProcessInstanceEntity(
            ProcessInstanceState.ACTIVE, processDefinitionKey, DEMO_BPMN_PROCESS_ID, true);
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 1, 0));
    // entities #4,5,6
    for (int i = 4; i <= 6; i++) {
      entities.add(
          createProcessInstanceEntity(
              ProcessInstanceState.ACTIVE, processDefinitionKey, DEMO_BPMN_PROCESS_ID));
    }

    // Demo process v2
    processDefinitionKey = processVersions.get(1).getKey();
    // instance #1
    processInstance =
        createProcessInstanceEntity(
            ProcessInstanceState.ACTIVE, processDefinitionKey, DEMO_BPMN_PROCESS_ID, true);
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 2, 0, true));
    // entities #2-7
    for (int i = 2; i <= 7; i++) {
      entities.add(
          createProcessInstanceEntity(
              ProcessInstanceState.ACTIVE, processDefinitionKey, DEMO_BPMN_PROCESS_ID));
    }
    // entities #8-9
    for (int i = 8; i <= 9; i++) {
      entities.add(
          createProcessInstanceEntity(
              ProcessInstanceState.COMPLETED, processDefinitionKey, DEMO_BPMN_PROCESS_ID));
    }

    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }

  private void createOrderProcessData() {
    final List<ProcessEntity> processVersions =
        createProcessVersions(ORDER_BPMN_PROCESS_ID, ORDER_PROCESS_NAME, 2, tenantId2);
    searchTestRule.persistNew(processVersions.toArray(new ExporterEntity[processVersions.size()]));

    final List<ExporterEntity> entities = new ArrayList<>();
    // Order process v1
    Long processDefinitionKey = processVersions.get(0).getKey();
    // entities #1-5
    for (int i = 1; i <= 5; i++) {
      entities.add(
          createProcessInstanceEntity(
              ProcessInstanceState.ACTIVE, processDefinitionKey, ORDER_BPMN_PROCESS_ID));
    }

    // Order process v2
    processDefinitionKey = processVersions.get(1).getKey();
    // instance #1
    ProcessInstanceForListViewEntity processInstance =
        createProcessInstanceEntity(
            ProcessInstanceState.ACTIVE, processDefinitionKey, ORDER_BPMN_PROCESS_ID);
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 0, 1));
    // instance #2
    processInstance =
        createProcessInstanceEntity(
            ProcessInstanceState.ACTIVE, processDefinitionKey, ORDER_BPMN_PROCESS_ID, true);
    entities.add(processInstance);
    entities.addAll(createIncidents(processInstance, 2, 0));
    // entities #3,4
    for (int i = 3; i <= 4; i++) {
      entities.add(
          createProcessInstanceEntity(
              ProcessInstanceState.ACTIVE, processDefinitionKey, ORDER_BPMN_PROCESS_ID));
    }

    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }

  private void createLoanProcessData() {
    // Loan process v1
    final List<ProcessEntity> processVersions =
        createProcessVersions(LOAN_BPMN_PROCESS_ID, LOAN_PROCESS_NAME, 1, tenantId1);
    searchTestRule.persistNew(processVersions.get(0));

    final List<ExporterEntity> entities = new ArrayList<>();
    final Long processDefinitionKey = processVersions.get(0).getKey();
    // entities #1-3
    for (int i = 1; i <= 3; i++) {
      final ProcessInstanceForListViewEntity processInstance =
          createProcessInstanceEntity(
              ProcessInstanceState.ACTIVE, processDefinitionKey, LOAN_BPMN_PROCESS_ID);
      entities.add(processInstance);
      entities.addAll(createIncidents(processInstance, 0, 2));
    }
    // entities #4-5
    for (int i = 4; i <= 5; i++) {
      entities.add(
          createProcessInstanceEntity(
              ProcessInstanceState.ACTIVE, processDefinitionKey, LOAN_BPMN_PROCESS_ID));
    }

    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }

  private void createNoInstancesProcessData(final int versionCount) {
    createProcessVersions(
            NO_INSTANCES_PROCESS_ID, NO_INSTANCES_PROCESS_NAME, versionCount, tenantId2)
        .forEach(processVersion -> searchTestRule.persistNew(processVersion));
  }

  private List<IncidentsByProcessGroupStatisticsDto> requestIncidentsByProcess() throws Exception {
    return mockMvcTestRule.listFromResponse(
        getRequest(QUERY_INCIDENTS_BY_PROCESS_URL), IncidentsByProcessGroupStatisticsDto.class);
  }

  private List<IncidentsByErrorMsgStatisticsDto> requestIncidentsByError() throws Exception {
    return mockMvcTestRule.listFromResponse(
        getRequest(QUERY_INCIDENTS_BY_ERROR_URL), IncidentsByErrorMsgStatisticsDto.class);
  }

  /**
   * Demo process v1 - 6 running instances: 3 active incidents, 2 resolved Demo process v2 - 2
   * finished instances, 7 running: 2 active in 1 inst, 0 resolved Order process v1 - 5 running
   * instances: no incidents Order process v2 - 4 running instances: 2 active in 1 inst, 1 resolved
   * Loan process v1 - 5 running instances: 0 active, 6 resolved
   */
  private void createData() {
    createDemoProcessData();
    createOrderProcessData();
    createLoanProcessData();
  }

  private List<ExporterEntity> createIncidents(
      final ProcessInstanceForListViewEntity processInstance,
      final int activeIncidentsCount,
      final int resolvedIncidentsCount) {
    return createIncidents(processInstance, activeIncidentsCount, resolvedIncidentsCount, false);
  }

  private List<ExporterEntity> createIncidents(
      final ProcessInstanceForListViewEntity processInstance,
      final int activeIncidentsCount,
      final int resolvedIncidentsCount,
      final boolean withOtherMsg) {
    final List<ExporterEntity> entities = new ArrayList<>();
    for (int i = 0; i < activeIncidentsCount; i++) {
      final FlowNodeInstanceForListViewEntity activityInstance =
          TestUtil.createFlowNodeInstance(
              processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE);
      createIncident(activityInstance, withOtherMsg ? ERRMSG_OTHER : null);
      entities.add(activityInstance);
      final IncidentEntity incidentEntity =
          TestUtil.createIncident(
              IncidentState.ACTIVE,
              activityInstance.getActivityId(),
              Long.valueOf(activityInstance.getId()),
              activityInstance.getErrorMessage());
      incidentEntity.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
      incidentEntity.setProcessInstanceKey(processInstance.getProcessInstanceKey());
      incidentEntity.setBpmnProcessId(processInstance.getBpmnProcessId());
      entities.add(incidentEntity);
    }
    for (int i = 0; i < resolvedIncidentsCount; i++) {
      final FlowNodeInstanceForListViewEntity activityInstance =
          TestUtil.createFlowNodeInstance(
              processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE);
      entities.add(activityInstance);
    }
    final int pendingIncidentsCount = random.nextInt(5) + 1;
    for (int i = 0; i < pendingIncidentsCount; i++) {
      final FlowNodeInstanceForListViewEntity activityInstance =
          TestUtil.createFlowNodeInstance(
              processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE);
      createIncident(activityInstance, withOtherMsg ? ERRMSG_OTHER : null);
      entities.add(activityInstance);
      final IncidentEntity incidentEntity =
          TestUtil.createIncident(
              IncidentState.ACTIVE,
              activityInstance.getActivityId(),
              Long.valueOf(activityInstance.getId()),
              activityInstance.getErrorMessage());
      incidentEntity.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
      incidentEntity.setProcessInstanceKey(processInstance.getProcessInstanceKey());
      incidentEntity.setBpmnProcessId(processInstance.getBpmnProcessId());
      incidentEntity.setState(IncidentState.PENDING);
      entities.add(incidentEntity);
    }
    return entities;
  }
}
