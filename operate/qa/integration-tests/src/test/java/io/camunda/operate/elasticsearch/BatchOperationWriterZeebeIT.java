/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch queries for operations. */
public class BatchOperationWriterZeebeIT extends OperateZeebeAbstractIT {

  private static final String QUERY_CREATE_BATCH_OPERATIONS_URL =
      ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  private static final IdentityPermission UPDATE = IdentityPermission.UPDATE_PROCESS_INSTANCE;
  private static final IdentityPermission DELETE = IdentityPermission.DELETE_PROCESS_INSTANCE;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  private final String bpmnProcessId1 = "bpmnProcessId1";
  private final String bpmnProcessId2 = "bpmnProcessId2";
  private final String bpmnProcessId3 = "bpmnProcessId3";
  @Autowired private BatchOperationWriter batchOperationWriter;
  @Autowired private OperationTemplate operationTemplate;
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testBatchUpdateWithPermisssionWhenAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(UPDATE))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(3);
    assertThat(response.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  public void testBatchUpdateWithPermisssionWhenNotAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(UPDATE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(0);
    assertThat(response.getOperationsTotalCount()).isEqualTo(0);
  }

  @Test
  public void testBatchUpdateWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(UPDATE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1)));

    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(1);
    assertThat(response.getOperationsTotalCount()).isEqualTo(1);
  }

  @Test
  public void testBatchDeleteWithPermisssionWhenAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(DELETE))
        .thenReturn(PermissionsService.ResourcesAllowed.all());

    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(3);
    assertThat(response.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  public void testBatchDeleteWithPermisssionWhenNotAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(DELETE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(0);
    assertThat(response.getOperationsTotalCount()).isEqualTo(0);
  }

  @Test
  public void testBatchDeleteWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    createData();
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.getProcessesWithPermission(DELETE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1)));
    MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(1);
    assertThat(response.getOperationsTotalCount()).isEqualTo(1);
  }

  private void createData() {
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    processInstance1.setProcessDefinitionKey(processInstance1.getProcessInstanceKey() + 1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    processInstance2.setProcessDefinitionKey(processInstance2.getProcessInstanceKey() + 1);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    processInstance3.setProcessDefinitionKey(processInstance3.getProcessInstanceKey() + 1);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);
  }

  @Test
  public void shouldValidateMigrationPlanForMigrateProcessInstance1() throws Exception {

    // given
    MigrationPlanDto invalidPlan = new MigrationPlanDto().setTargetProcessDefinitionKey("123");

    // when
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(invalidPlan);

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldValidateMigrationPlanForMigrateProcessInstance2() throws Exception {

    // given
    MigrationPlanDto invalidPlan =
        new MigrationPlanDto()
            .setMappingInstructions(
                List.of(
                    new MigrationPlanDto.MappingInstruction()
                        .setSourceElementId("source")
                        .setTargetElementId("target")));

    // when
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(invalidPlan);

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldValidateMigrationPlanForMigrateProcessInstance3() throws Exception {

    // given
    MigrationPlanDto invalidPlan =
        new MigrationPlanDto()
            .setTargetProcessDefinitionKey("123")
            .setMappingInstructions(List.of());

    // when
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(invalidPlan);

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldCreateMigrateProcessInstanceOperations() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey =
        tester
            .deployProcess("single-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("process", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processInstanceKey2 =
        tester
            .startProcessInstance("process", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(
                Arrays.asList(
                    String.valueOf(processInstanceKey1), String.valueOf(processInstanceKey2)));

    // when
    when(permissionsService.getProcessesWithPermission(any()))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey("123")
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("source")
                                .setTargetElementId("target"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);

    // then
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations)
        .extracting("type")
        .containsExactly(request.getOperationType(), request.getOperationType());
    assertThat(operations)
        .extracting("batchOperationId")
        .containsExactly(batchOperationEntity.getId(), batchOperationEntity.getId());
    assertThat(operations)
        .extracting("migrationPlan")
        .containsExactly(migrationPlanJson, migrationPlanJson);
    assertThat(operations)
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
  }
}
