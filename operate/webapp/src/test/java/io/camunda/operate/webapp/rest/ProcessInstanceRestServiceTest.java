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
package io.camunda.operate.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.validation.ModifyProcessInstanceRequestValidator;
import io.camunda.operate.webapp.rest.validation.ProcessInstanceRequestValidator;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceRestServiceTest {

  @Mock private PermissionsService permissionsService;
  @Mock private ProcessInstanceRequestValidator processInstanceRequestValidator;
  @Mock private ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator;
  @Mock private BatchOperationWriter batchOperationWriter;
  @Mock private ProcessInstanceReader processInstanceReader;
  @Mock private ListViewReader listViewReader;
  @Mock private IncidentReader incidentReader;
  @Mock private VariableReader variableReader;
  @Mock private FlowNodeInstanceReader flowNodeInstanceReader;
  @Mock private FlowNodeStatisticsReader flowNodeStatisticsReader;
  @Mock private SequenceFlowStore sequenceFlowStore;

  private ProcessInstanceRestService underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new ProcessInstanceRestService(
            permissionsService,
            processInstanceRequestValidator,
            modifyProcessInstanceRequestValidator,
            batchOperationWriter,
            processInstanceReader,
            listViewReader,
            incidentReader,
            variableReader,
            flowNodeInstanceReader,
            flowNodeStatisticsReader,
            sequenceFlowStore);

    when(permissionsService.hasPermissionForProcess(any(), any())).thenReturn(true);
  }

  @Test
  public void testGetInstanceByIdWithValidId() {
    // given
    final String validId = "123";
    // when
    final ListViewProcessInstanceDto expectedDto = new ListViewProcessInstanceDto().setId("one id");
    when(processInstanceReader.getProcessInstanceWithOperationsByKey(Long.valueOf(validId)))
        .thenReturn(expectedDto);
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(validId)))
        .thenReturn(new ProcessInstanceForListViewEntity());
    // then
    final ListViewProcessInstanceDto actualResult = underTest.queryProcessInstanceById(validId);
    assertThat(actualResult).isEqualTo(expectedDto);
  }

  @Test
  public void testProcessInstanceByIdFailsWhenNoPermissions() {
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.queryProcessInstanceById(processInstanceId));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceIncidentsFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.queryIncidentsByProcessInstanceId(processInstanceId));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceSequenceFlowsFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.querySequenceFlowsByProcessInstanceId(processInstanceId));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceVariablesFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.getVariables(processInstanceId, new VariableRequestDto()));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeStatesFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class, () -> underTest.getFlowNodeStates(processInstanceId));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceStatisticsFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class, () -> underTest.getStatistics(processInstanceId));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeMetadataFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.getFlowNodeMetadata(processInstanceId, new FlowNodeMetadataRequestDto()));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }

  @Test
  public void testProcessInstanceDeleteOperationFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.DELETE_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.operation(
                    processInstanceId,
                    new CreateOperationRequestDto()
                        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)));

    assertThat(exception.getMessage())
        .contains("No DELETE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceUpdateOperationFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.UPDATE_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.operation(
                    processInstanceId,
                    new CreateOperationRequestDto()
                        .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)));

    assertThat(exception.getMessage())
        .contains("No UPDATE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceModifyOperationFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, IdentityPermission.UPDATE_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.operation(
                    processInstanceId,
                    new CreateOperationRequestDto().setOperationType(OperationType.ADD_VARIABLE)));

    assertThat(exception.getMessage())
        .contains("No UPDATE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceSingleVariableFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class, () -> underTest.getVariable(processInstanceId, "var1"));

    assertThat(exception.getMessage()).contains("No READ permission for process instance");
  }
}
