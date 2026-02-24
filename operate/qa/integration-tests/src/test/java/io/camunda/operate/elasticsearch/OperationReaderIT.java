/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllRunningRequest;
import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.util.TestUtil.createVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Tests retrieval of operation taking into account BATCH READ permissions. */
public class OperationReaderIT extends OperateAbstractIT {

  private static final Long PROCESS_KEY_DEMO_PROCESS = 42L;
  private static final String VARNAME_1 = "var1";
  private static final String VARNAME_2 = "var2";
  private static final String VARNAME_3 = "var3";
  private static final long INCIDENT_1 = 1;
  private static final long INCIDENT_2 = 2;
  private static final long INCIDENT_3 = 3;
  private static String processInstanceId1;
  private static String processInstanceId2;
  private static String processInstanceId3;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockitoBean PermissionsService permissionsService;
  @Autowired private IncidentReader incidentReader;
  @Autowired private ListViewReader listViewReader;
  @Autowired private ProcessInstanceReader processInstanceReader;

  @Override
  @Before
  public void before() {
    super.before();
    when(permissionsService.getBatchOperationsWithPermission(PermissionType.READ))
        .thenReturn(ResourcesAllowed.wildcard());
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(ResourcesAllowed.wildcard());
    when(permissionsService.hasPermissionForProcess(
            any(), eq(PermissionType.READ_PROCESS_INSTANCE)))
        .thenReturn(true);
    createData(PROCESS_KEY_DEMO_PROCESS);
  }

  @Test
  public void testProcessInstanceQuery() throws Exception {

    final ListViewRequestDto processInstanceQueryDto = createGetAllRunningRequest();
    final ListViewResponseDto response =
        listViewReader.queryProcessInstances(processInstanceQueryDto);

    final List<ListViewProcessInstanceDto> processInstances = response.getProcessInstances();
    assertThat(processInstances).hasSize(3);
    assertThat(processInstances)
        .filteredOn("id", processInstanceId1)
        .allMatch(pi -> pi.isHasActiveOperation() && pi.getOperations().size() == 3);
    assertThat(processInstances)
        .filteredOn("id", processInstanceId2)
        .allMatch(pi -> pi.isHasActiveOperation() && pi.getOperations().size() == 3);
    assertThat(processInstances)
        .filteredOn("id", processInstanceId3)
        .allMatch(pi -> !pi.isHasActiveOperation() && pi.getOperations().size() == 2);
  }

  @Test
  public void testProcessInstanceQueryWithNoPermission() throws Exception {
    when(permissionsService.getBatchOperationsWithPermission(PermissionType.READ))
        .thenReturn(ResourcesAllowed.withIds(Set.of()));
    final ListViewRequestDto processInstanceQueryDto = createGetAllRunningRequest();
    final ListViewResponseDto response =
        listViewReader.queryProcessInstances(processInstanceQueryDto);

    final List<ListViewProcessInstanceDto> processInstances = response.getProcessInstances();
    assertThat(processInstances).hasSize(3);
    assertThat(processInstances)
        .allMatch(pi -> !pi.isHasActiveOperation() && pi.getOperations().size() == 0);
  }

  @Test
  public void testQueryIncidentsByProcessInstanceId() throws Exception {
    final IncidentResponseDto response =
        incidentReader.getIncidentsByProcessInstanceId(processInstanceId1);

    final List<IncidentDto> incidents = response.getIncidents();
    assertThat(incidents).hasSize(3);
    assertThat(incidents)
        .filteredOn("id", INCIDENT_1)
        .allMatch(inc -> inc.isHasActiveOperation() && inc.getLastOperation() != null);
    assertThat(incidents)
        .filteredOn("id", INCIDENT_2)
        .allMatch(inc -> inc.isHasActiveOperation() && inc.getLastOperation() != null);
    assertThat(incidents)
        .filteredOn("id", INCIDENT_3)
        .allMatch(inc -> !inc.isHasActiveOperation() && inc.getLastOperation() == null);
  }

  @Test
  public void testQueryProcessInstanceById() throws Exception {
    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(
            Long.valueOf(processInstanceId3));

    assertThat(processInstance.getOperations()).hasSize(2);
    assertThat(processInstance.isHasActiveOperation()).isFalse();
  }

  /** */
  protected void createData(final Long processDefinitionKey) {

    final List<ExporterEntity> entities = new ArrayList<>();

    ProcessInstanceForListViewEntity inst =
        createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    processInstanceId1 = String.valueOf(inst.getKey());
    entities.add(
        createIncident(
            IncidentState.ACTIVE,
            INCIDENT_1,
            Long.valueOf(processInstanceId1),
            processDefinitionKey));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), INCIDENT_1, null));
    entities.add(
        createIncident(
            IncidentState.ACTIVE,
            INCIDENT_2,
            Long.valueOf(processInstanceId1),
            processDefinitionKey));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), INCIDENT_2, null));
    entities.add(
        createIncident(
            IncidentState.ACTIVE,
            INCIDENT_3,
            Long.valueOf(processInstanceId1),
            processDefinitionKey));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), INCIDENT_3, null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    final long processInstanceKey = inst.getKey();
    processInstanceId2 = String.valueOf(inst.getKey());
    entities.add(createVariable(processInstanceKey, processInstanceKey, VARNAME_1, "value"));
    entities.add(
        TestUtil.createOperationEntity(
            inst.getProcessInstanceKey(), null, VARNAME_1, OperationState.COMPLETED, null, false));
    entities.add(createVariable(processInstanceKey, processInstanceKey, VARNAME_2, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, VARNAME_2));
    entities.add(createVariable(processInstanceKey, processInstanceKey, VARNAME_3, "value"));
    entities.add(TestUtil.createOperationEntity(inst.getProcessInstanceKey(), null, VARNAME_3));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    processInstanceId3 = String.valueOf(inst.getKey());
    entities.add(
        TestUtil.createOperationEntity(
            inst.getProcessInstanceKey(), null, null, OperationState.FAILED, null, false));
    entities.add(
        TestUtil.createOperationEntity(
            inst.getProcessInstanceKey(), null, null, OperationState.COMPLETED, null, false));
    entities.add(inst);

    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }
}
