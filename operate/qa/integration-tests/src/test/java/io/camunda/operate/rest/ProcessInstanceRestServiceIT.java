/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

public class ProcessInstanceRestServiceIT extends OperateAbstractIT {

  @MockBean private ProcessInstanceReader processInstanceReader;

  @MockBean private VariableReader variableReader;

  @MockBean private BatchOperationWriter batchOperationWriter;

  @MockBean private PermissionsService permissionsService;

  @Override
  @Before
  public void before() {
    super.before();
    when(permissionsService.hasPermissionForProcess(any(), any())).thenReturn(true);
  }

  @Test
  public void testQueryWithWrongSortBy() throws Exception {
    // when
    final String jsonRequest =
        "{ \"sorting\": {\"sortBy\": \"processId\",\"sortOrder\": \"asc\"}}"; // not allowed for
    // sorting
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100), jsonRequest);
    // then
    assertErrorMessageContains(mvcResult, "SortBy");
  }

  @Test
  public void testQueryWithWrongSortOrder() throws Exception {
    // when
    final String jsonRequest =
        "{ \"sorting\": {\"sortBy\": \"id\",\"sortOrder\": \"unknown\"}}"; // wrong sort order
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100), jsonRequest);
    // then
    assertErrorMessageContains(mvcResult, "SortOrder");
  }

  private String query(final int firstResult, final int maxResults) {
    return String.format(
        "%s?firstResult=%d&maxResults=%d",
        ProcessInstanceRestService.PROCESS_INSTANCE_URL, firstResult, maxResults);
  }

  @Test
  public void testOperationForUpdateVariableFailsNoValue() throws Exception {
    final CreateOperationRequestDto operationRequestDto =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableScopeId("a");
    operationRequestDto.setVariableName("a");
    final MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(
        mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationForUpdateVariableFailsNoName() throws Exception {
    final CreateOperationRequestDto operationRequestDto =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableScopeId("a");
    operationRequestDto.setVariableValue("a");
    final MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(
        mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationForUpdateVariableFailsNoScopeId() throws Exception {
    final CreateOperationRequestDto operationRequestDto =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableName("a");
    operationRequestDto.setVariableValue("a");
    final MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(
        mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationFailsNoOperationType() throws Exception {
    final CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto();
    final MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "Operation type must be defined.");
  }

  @Test
  public void testBatchOperationForUpdateVariableFailsNoQuery() throws Exception {
    final CreateBatchOperationRequestDto operationRequestDto =
        new CreateBatchOperationRequestDto(null, OperationType.UPDATE_VARIABLE);
    final MvcResult mvcResult =
        postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "List view query must be defined.");
  }

  @Test
  public void testBatchOperationForUpdateVariableFailsWrongEndpoint() throws Exception {
    final CreateBatchOperationRequestDto operationRequestDto =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.UPDATE_VARIABLE);
    final MvcResult mvcResult =
        postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(
        mvcResult,
        "For variable update use \"Create operation for one process instance\" endpoint.");
  }

  @Test
  public void testBatchOperationFailsNoOperationType() throws Exception {
    final CreateBatchOperationRequestDto operationRequestDto =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), null);
    final MvcResult mvcResult =
        postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
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

  private void getRequestShouldFailValidationForUrl(final String url) throws Exception {
    final MvcResult mvcResult =
        getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertErrorMessageContains(mvcResult, "Specified ID is not valid");
  }

  private void postRequestShouldFailValidationForUrl(final String url) throws Exception {
    final MvcResult mvcResult =
        postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertErrorMessageContains(mvcResult, "Specified ID is not valid");
  }

  @Test
  public void testGetInstanceByIdWithValidId() throws Exception {
    // given
    final String validId = "123";
    // when
    final ListViewProcessInstanceDto expectedDto = new ListViewProcessInstanceDto().setId("one id");
    when(processInstanceReader.getProcessInstanceWithOperationsByKey(123L)).thenReturn(expectedDto);
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(validId)))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult = getRequest(getInstanceByIdUrl(validId));
    // then
    final ListViewProcessInstanceDto actualResult =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    Assert.assertEquals(expectedDto, actualResult);
  }

  @Test
  public void testModifyFailsForNotExistingProcessInstance() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L)).thenReturn(null);
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify", new ModifyProcessInstanceRequestDto());
    assertErrorMessageContains(mvcResult, "Process instance with key 123 does not exist");
  }

  @Test
  public void testModifyFailsForNotExistingModifications() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setProcessInstanceKey("123")
                .setModifications(null));
    assertErrorMessageContains(
        mvcResult, "No modifications given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForMissingAddParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(new Modification().setModification(Modification.Type.ADD_TOKEN))));
    assertErrorMessageContains(
        mvcResult, "No toFlowNodeId given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForMissingCancelParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(new Modification().setModification(Modification.Type.CANCEL_TOKEN))));
    assertErrorMessageContains(
        mvcResult,
        "Neither fromFlowNodeId nor fromFlowNodeInstanceKey is given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForWrongFlowNodeInstanceCancelParameter() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(
                        new Modification()
                            .setFromFlowNodeInstanceKey("no long")
                            .setModification(Modification.Type.CANCEL_TOKEN))));
    assertErrorMessageContains(mvcResult, "fromFlowNodeInstanceKey should be a Long.");
  }

  @Test
  public void testModifyFailsForTooManyCancelParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(
                        new Modification()
                            .setFromFlowNodeId("toFlowNodeId")
                            .setFromFlowNodeInstanceKey("1234")
                            .setModification(Modification.Type.CANCEL_TOKEN))));
    assertErrorMessageContains(
        mvcResult,
        "Either fromFlowNodeId or fromFlowNodeInstanceKey for process instance with key 123 should be given, not both.");
  }

  @Test
  public void testModifyFailsForMissingMoveParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(new Modification().setModification(Modification.Type.MOVE_TOKEN))));
    assertErrorMessageContains(
        mvcResult, "No toFlowNodeId given for process instance with key 123");
  }

  @Test
  public void testModifyFailsForTooManyMoveParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(
                        new Modification()
                            .setToFlowNodeId("toFlowNodeId")
                            .setFromFlowNodeInstanceKey("123")
                            .setFromFlowNodeId("fromFlowNodeId")
                            .setModification(Modification.Type.MOVE_TOKEN))));
    assertErrorMessageContains(
        mvcResult,
        "Either fromFlowNodeId or fromFlowNodeInstanceKey for process instance with key 123 should be given, not both.");
  }

  @Test
  public void testModifyFailsForUnknownModification() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto().setModifications(List.of(new Modification())));
    assertErrorMessageContains(
        mvcResult, "Unknown Modification.Type given for process instance with key 123.");
  }

  @Test
  public void testModifyFailsForMissingAddOrEditVariableParameters() throws Exception {
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    // ADD_VARIABLE
    MvcResult mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(new Modification().setModification(Modification.Type.ADD_VARIABLE))));
    assertErrorMessageContains(mvcResult, "No variables given for process instance with key 123");

    // EDIT_VARIABLE
    mvcResult =
        postRequestThatShouldFail(
            getInstanceByIdUrl("123") + "/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(new Modification().setModification(Modification.Type.EDIT_VARIABLE))));
    assertErrorMessageContains(mvcResult, "No variables given for process instance with key 123");
  }

  @Test
  public void testProcessInstanceByIdFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getInstanceByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceIncidentsFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getIncidentsByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceSequenceFlowsFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getSequenceFlowsByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceVariablesFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(
            getVariablesByIdUrl(processInstanceId), new VariableRequestDto());
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeStatesFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getFlowNodeStatesByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceStatisticsFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getFlowNodeStatisticsByIdUrl(processInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeMetadataFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(
            getFlowNodeMetadataByIdUrl(processInstanceId), new FlowNodeMetadataRequestDto());
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceDeleteOperationFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    final CreateOperationRequestDto request =
        new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_INSTANCE);
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.DELETE_PROCESS_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(getOperationUrl(processInstanceId), request);
    // then
    assertErrorMessageContains(
        mvcResult, "No DELETE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceDeleteOperationOkWhenHasPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    final CreateOperationRequestDto request =
        new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_INSTANCE);
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.DELETE_PROCESS_INSTANCE))
        .thenReturn(true);
    when(batchOperationWriter.scheduleSingleOperation(Long.parseLong(processInstanceId), request))
        .thenReturn(new BatchOperationEntity());
    final MvcResult mvcResult = postRequest(getOperationUrl(processInstanceId), request);
    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
  }

  @Test
  public void testProcessInstanceUpdateOperationFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    final CreateOperationRequestDto request =
        new CreateOperationRequestDto().setOperationType(OperationType.CANCEL_PROCESS_INSTANCE);
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.UPDATE_PROCESS_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(getOperationUrl(processInstanceId), request);
    // then
    assertErrorMessageContains(
        mvcResult, "No UPDATE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceUpdateOperationOkWhenHasPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    final CreateOperationRequestDto request =
        new CreateOperationRequestDto().setOperationType(OperationType.CANCEL_PROCESS_INSTANCE);
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.UPDATE_PROCESS_INSTANCE))
        .thenReturn(true);
    when(batchOperationWriter.scheduleSingleOperation(Long.parseLong(processInstanceId), request))
        .thenReturn(new BatchOperationEntity());
    final MvcResult mvcResult = postRequest(getOperationUrl(processInstanceId), request);
    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
  }

  @Test
  public void testProcessInstanceModifyOperationFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    final ModifyProcessInstanceRequestDto.Modification modification =
        new Modification()
            .setModification(Modification.Type.ADD_VARIABLE)
            .setVariables(Map.of("var", 11));
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto().setModifications(List.of(modification));
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.UPDATE_PROCESS_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(getModificationUrl(processInstanceId), request);
    // then
    assertErrorMessageContains(
        mvcResult, "No UPDATE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceModifyOperationOkWhenHasPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    final ModifyProcessInstanceRequestDto.Modification modification =
        new Modification()
            .setModification(Modification.Type.ADD_VARIABLE)
            .setVariables(Map.of("var", 11));
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto().setModifications(List.of(modification));
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.UPDATE_PROCESS_INSTANCE))
        .thenReturn(true);
    when(batchOperationWriter.scheduleModifyProcessInstance(any()))
        .thenReturn(new BatchOperationEntity());
    final MvcResult mvcResult = postRequest(getModificationUrl(processInstanceId), request);
    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
  }

  @Test
  public void testProcessInstanceSingleVariableFailsWhenNoPermissions() throws Exception {
    // given
    final String variableId = "var1";
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getVariableUrl(processInstanceId, variableId));
    // then
    assertErrorMessageContains(mvcResult, "No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceSingleVariableOkWhenHasPermissions() throws Exception {
    // given
    final String variableId = "var1";
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(true);
    when(variableReader.getVariable(variableId)).thenReturn(new VariableDto());
    final MvcResult mvcResult = getRequest(getVariableUrl(processInstanceId, variableId));
    // then
    final VariableDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
  }

  public String getBatchOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  }

  public String getOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/111/operation";
  }

  public String getOperationUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/operation";
  }

  public String getModificationUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/modify";
  }

  public String getInstanceByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id;
  }

  public String getIncidentsByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/incidents";
  }

  public String getSequenceFlowsByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/sequence-flows";
  }

  public String getVariablesByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/variables";
  }

  public String getVariableUrl(final String processInstanceId, final String variableId) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL
        + "/"
        + processInstanceId
        + "/variables/"
        + variableId;
  }

  public String getFlowNodeStatesByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/flow-node-states";
  }

  public String getFlowNodeStatisticsByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/statistics";
  }

  public String getFlowNodeMetadataByIdUrl(final String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/flow-node-metadata";
  }
}
