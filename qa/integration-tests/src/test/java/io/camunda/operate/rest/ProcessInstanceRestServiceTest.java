/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.es.reader.ActivityStatisticsReader;
import io.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.webapp.es.reader.SequenceFlowReader;
import io.camunda.operate.webapp.es.reader.VariableReader;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.*;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.zeebe.operation.ModifyProcessInstanceRequestValidator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.validation.ConstraintViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        ProcessInstanceRestService.class,
        JacksonConfig.class,
        OperateProperties.class,
        OperateProfileService.class,
        ModifyProcessInstanceRequestValidator.class,
        JacksonConfig.class, 
        OperateProperties.class
    }
)
public class ProcessInstanceRestServiceTest extends OperateIntegrationTest {

  @MockBean
  private ListViewReader listViewReader;

  @MockBean
  private ActivityStatisticsReader activityStatisticsReader;

  @MockBean
  private ProcessInstanceReader processInstanceReader;

  @MockBean
  private IncidentReader incidentReader;

  @MockBean
  private VariableReader variableReader;

  @MockBean
  private SequenceFlowReader sequenceFlowReader;

  @MockBean
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @MockBean
  private BatchOperationWriter batchOperationWriter;

  @MockBean
  private OperationReader operationReader;

  @MockBean
  private PermissionsService permissionsService;

  @Before
  public void before()
  {
    super.before();
    when(permissionsService.hasPermissionForProcess(any(), any())).thenReturn(true);
  }

  @Test
  public void testQueryWithWrongSortBy() throws Exception {
    //when
    String jsonRequest = "{ \"sorting\": {\"sortBy\": \"processId\",\"sortOrder\": \"asc\"}}";     //not allowed for sorting
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),jsonRequest);
    //then
    assertErrorMessageContains(mvcResult, "SortBy");
  }

  @Test
  public void testQueryWithWrongSortOrder() throws Exception {
    //when
    String jsonRequest = "{ \"sorting\": {\"sortBy\": \"id\",\"sortOrder\": \"unknown\"}}";     //wrong sort order
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),jsonRequest);
    //then
    assertErrorMessageContains(mvcResult, "SortOrder");
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", ProcessInstanceRestService.PROCESS_INSTANCE_URL, firstResult, maxResults);
  }

  @Test
  public void testOperationForUpdateVariableFailsNoValue() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableScopeId("a");
    operationRequestDto.setVariableName("a");
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationForUpdateVariableFailsNoName() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableScopeId("a");
    operationRequestDto.setVariableValue("a");
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationForUpdateVariableFailsNoScopeId() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableName("a");
    operationRequestDto.setVariableValue("a");
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationFailsNoOperationType() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto();
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "Operation type must be defined.");
  }

  @Test
  public void testBatchOperationForUpdateVariableFailsNoQuery() throws Exception {
    CreateBatchOperationRequestDto operationRequestDto = new CreateBatchOperationRequestDto(null, OperationType.UPDATE_VARIABLE);
    MvcResult mvcResult = postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "List view query must be defined.");
  }

  @Test
  public void testBatchOperationForUpdateVariableFailsWrongEndpoint() throws Exception {
    CreateBatchOperationRequestDto operationRequestDto = new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.UPDATE_VARIABLE);
    MvcResult mvcResult = postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "For variable update use \"Create operation for one process instance\" endpoint.");
  }

  @Test
  public void testBatchOperationFailsNoOperationType() throws Exception {
    CreateBatchOperationRequestDto operationRequestDto = new CreateBatchOperationRequestDto(new ListViewQueryDto(), null);
    MvcResult mvcResult = postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "Operation type must be defined.");
  }

  @Test
  public void testGetInstanceByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getInstanceByIdUrl("4503599627535750:"));
  }

  @Test
  public void testGetInstanceByIdWithIdValueNull() throws Exception {
    getRequestShouldFailValidationForUrl(getInstanceByIdUrl("null"));
  }

  @Test
  public void testGetIncidentsByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getIncidentsByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetSequenceFlowsByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getSequenceFlowsByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetVariablesByIdWithInvalidId() throws Exception {
    postRequestShouldFailValidationForUrl(getVariablesByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getFlowNodeStatesByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetFlowNodeMetadataByIdWithInvalidId() throws Exception {
    postRequestShouldFailValidationForUrl(getFlowNodeMetadataByIdUrl("not-valid-id-123"));
  }

  private void getRequestShouldFailValidationForUrl(String url) throws Exception {
    MvcResult mvcResult = getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertErrorMessageContains(mvcResult, "Specified ID is not valid");
  }

  private void postRequestShouldFailValidationForUrl(String url) throws Exception {
    MvcResult mvcResult = postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertErrorMessageContains(mvcResult, "Specified ID is not valid");
  }

  @Test
  public void testGetInstanceByIdWithValidId() throws Exception {
    // given
    String validId = "123";
    // when
    ListViewProcessInstanceDto expectedDto = new ListViewProcessInstanceDto().setId("one id");
    when(processInstanceReader.getProcessInstanceWithOperationsByKey(123L)).thenReturn(expectedDto);
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(validId))).thenReturn(new ProcessInstanceForListViewEntity());
    MvcResult mvcResult = getRequest(getInstanceByIdUrl(validId));
    // then
    ListViewProcessInstanceDto actualResult = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    Assert.assertEquals(expectedDto, actualResult);
  }

  @Test
  public void testModifyFailsForNotExistingProcessInstance() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(null);
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify", new ModifyProcessInstanceRequestDto());
    assertErrorMessageContains( mvcResult,"Process instance with key 123 does not exist");
  }

  @Test
  public void testModifyFailsForNotExistingModifications() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setProcessInstanceKey("123").setModifications(null));
    assertErrorMessageContains( mvcResult,"No modifications given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForMissingAddParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setModification(Modification.Type.ADD_TOKEN))));
    assertErrorMessageContains( mvcResult,"No toFlowNodeId given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForMissingCancelParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setModification(Modification.Type.CANCEL_TOKEN))));
    assertErrorMessageContains( mvcResult,"Neither fromFlowNodeId nor fromFlowNodeInstanceKey is given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForWrongFlowNodeInstanceCancelParameter() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setFromFlowNodeInstanceKey("no long")
            .setModification(Modification.Type.CANCEL_TOKEN))));
    assertErrorMessageContains( mvcResult,"fromFlowNodeInstanceKey should be a Long.");
  }

  @Test
  public void testModifyFailsForTooManyCancelParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
                .setFromFlowNodeId("toFlowNodeId")
                .setFromFlowNodeInstanceKey("1234")
            .setModification(Modification.Type.CANCEL_TOKEN))));
    assertErrorMessageContains( mvcResult,"Either fromFlowNodeId or fromFlowNodeInstanceKey for process instance with key 123 should be given, not both.");
  }

  @Test
  public void testModifyFailsForMissingMoveParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setModification(Modification.Type.MOVE_TOKEN))));
    assertErrorMessageContains( mvcResult,"No toFlowNodeId given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForTooManyMoveParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setToFlowNodeId("toFlowNodeId")
            .setFromFlowNodeInstanceKey("123")
            .setFromFlowNodeId("fromFlowNodeId")
            .setModification(Modification.Type.MOVE_TOKEN))));
    assertErrorMessageContains( mvcResult,"Either fromFlowNodeId or fromFlowNodeInstanceKey for process instance with key 123 should be given, not both.");
  }

  @Test
  public void testModifyFailsForUnknownModification() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification())));
    assertErrorMessageContains( mvcResult,"Unknown Modification.Type given for process instance with key 123.");
  }

  @Test
  public void testModifyFailsForMissingAddOrEditVariableParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(new ProcessInstanceForListViewEntity());
    // ADD_VARIABLE
    MvcResult mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setModification(Modification.Type.ADD_VARIABLE))));
    assertErrorMessageContains( mvcResult,"No variables given for process instance with key 123");

    // EDIT_VARIABLE
    mvcResult = postRequestThatShouldFail(getInstanceByIdUrl("123") + "/modify",
        new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification()
            .setModification(Modification.Type.EDIT_VARIABLE))));
    assertErrorMessageContains( mvcResult,"No variables given for process instance with key 123");
  }

  @Test
  public void testProcessInstanceByIdFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getInstanceByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceIncidentsFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getIncidentsByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceSequenceFlowsFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getSequenceFlowsByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceVariablesFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = postRequestShouldFailWithNoAuthorization(getVariablesByIdUrl(processInstanceId), new VariableRequestDto());
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeStatesFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getFlowNodeStatesByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceStatisticsFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getFlowNodeStatisticsByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeMetadataFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = postRequestShouldFailWithNoAuthorization(getFlowNodeMetadataByIdUrl(processInstanceId), new FlowNodeMetadataRequestDto());
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceDeleteOperationFailsWhenNoPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    CreateOperationRequestDto request = new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_INSTANCE);
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(
        new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE_PROCESS_INSTANCE)).thenReturn(false);
    MvcResult mvcResult = postRequestShouldFailWithNoAuthorization(getOperationUrl(processInstanceId), request);
    // then
    assertErrorMessageContains(mvcResult, "No DELETE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceDeleteOperationOkWhenHasPermissions() throws Exception {
    // given
    String processInstanceId = "123";
    String bpmnProcessId = "processId";
    CreateOperationRequestDto request = new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_INSTANCE);
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId))).thenReturn(
        new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE_PROCESS_INSTANCE)).thenReturn(true);
    when(batchOperationWriter.scheduleSingleOperation(Long.parseLong(processInstanceId), request)).thenReturn(new BatchOperationEntity());
    MvcResult mvcResult = postRequest(getOperationUrl(processInstanceId), request);
    // then
    final BatchOperationEntity response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });
    assertThat(response).isNotNull();
  }

  public String getBatchOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  }

  public String getOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/111/operation";
  }

  public String getOperationUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/operation";
  }

  public String getInstanceByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id;
  }

  public String getIncidentsByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/incidents";
  }

  public String getSequenceFlowsByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/sequence-flows";
  }

  public String getVariablesByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/variables";
  }

  public String getFlowNodeStatesByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/flow-node-states";
  }

  public String getFlowNodeStatisticsByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/statistics";
  }

  public String getFlowNodeMetadataByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/flow-node-metadata";
  }

}
