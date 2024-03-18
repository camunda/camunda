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
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
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
  public void testGetInstanceByIdWithInvalidId() throws Exception {
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/4503599627535750:";
    final MvcResult mvcResult =
        getRequestShouldFailWithException(url, ConstraintViolationException.class);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetIncidentsByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/incidents";
    final MvcResult mvcResult =
        getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetVariablesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/variables";
    final MvcResult mvcResult =
        postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-states";
    final MvcResult mvcResult =
        getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeMetadataByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-metadata";
    final MvcResult mvcResult =
        postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
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
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + processInstanceId + "/operation";
    final MvcResult mvcResult = postRequest(url, request);
    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
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
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + processInstanceId + "/operation";
    final MvcResult mvcResult = postRequest(url, request);
    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
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
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + processInstanceId + "/modify";
    final MvcResult mvcResult = postRequest(url, request);
    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
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
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL
            + "/"
            + processInstanceId
            + "/variables/"
            + variableId;
    final MvcResult mvcResult = getRequest(url);
    // then
    final VariableDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).isNotNull();
  }
}
