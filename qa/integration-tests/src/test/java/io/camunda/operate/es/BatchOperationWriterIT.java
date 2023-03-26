/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests Elasticsearch queries for operations.
 */
public class BatchOperationWriterIT extends OperateIntegrationTest {

  private static final String QUERY_CREATE_BATCH_OPERATIONS_URL = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  private static final IdentityPermission UPDATE = IdentityPermission.UPDATE_PROCESS_INSTANCE;
  private static final IdentityPermission DELETE = IdentityPermission.DELETE_PROCESS_INSTANCE;

  private final String bpmnProcessId1 = "bpmnProcessId1";
  private final String bpmnProcessId2 = "bpmnProcessId2";
  private final String bpmnProcessId3 = "bpmnProcessId3";

  @MockBean
  private PermissionsService permissionsService;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testBatchUpdateWithPermisssionWhenAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE).setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(UPDATE)).thenReturn(
        PermissionsService.ResourcesAllowed.all());
    when(permissionsService.createQueryForProcessesByPermission(UPDATE)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.getInstancesCount()).isEqualTo(3);
    assertThat(response.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  public void testBatchUpdateWithPermisssionWhenNotAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE).setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(UPDATE)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of()));
    when(permissionsService.createQueryForProcessesByPermission(UPDATE)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.getInstancesCount()).isEqualTo(0);
    assertThat(response.getOperationsTotalCount()).isEqualTo(0);
  }

  @Test
  public void testBatchUpdateWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE).setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(UPDATE)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1)));
    when(permissionsService.createQueryForProcessesByPermission(UPDATE)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.getInstancesCount()).isEqualTo(1);
    assertThat(response.getOperationsTotalCount()).isEqualTo(1);
  }

  @Test
  public void testBatchDeleteWithPermisssionWhenAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE).setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(DELETE)).thenReturn(
        PermissionsService.ResourcesAllowed.all());
    when(permissionsService.createQueryForProcessesByPermission(DELETE)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.getInstancesCount()).isEqualTo(3);
    assertThat(response.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  public void testBatchDeleteWithPermisssionWhenNotAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE).setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(DELETE)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of()));
    when(permissionsService.createQueryForProcessesByPermission(DELETE)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.getInstancesCount()).isEqualTo(0);
    assertThat(response.getOperationsTotalCount()).isEqualTo(0);
  }

  @Test
  public void testBatchDeleteWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE).setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(DELETE)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1)));
    when(permissionsService.createQueryForProcessesByPermission(DELETE)).thenCallRealMethod();
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response.getInstancesCount()).isEqualTo(1);
    assertThat(response.getOperationsTotalCount()).isEqualTo(1);
  }

  private void createData() {
    final ProcessInstanceForListViewEntity processInstance1 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    processInstance1.setProcessDefinitionKey(processInstance1.getProcessInstanceKey() + 1);
    final ProcessInstanceForListViewEntity processInstance2 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    processInstance2.setProcessDefinitionKey(processInstance2.getProcessInstanceKey() + 1);
    final ProcessInstanceForListViewEntity processInstance3 = createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    processInstance3.setProcessDefinitionKey(processInstance3.getProcessInstanceKey() + 1);
    elasticsearchTestRule.persistNew(processInstance1, processInstance2, processInstance3);
  }
}
