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
package io.camunda.operate.it;

import static io.camunda.operate.qa.util.RestAPITestUtil.*;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.*;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.*;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto.MappingInstruction;
import io.camunda.operate.webapp.zeebe.operation.*;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Objects;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

@Ignore
public class MigrateProcessInstanceOperationZeebeIT extends OperateZeebeAbstractIT {

  @Autowired private MigrateProcessInstanceHandler migrateProcessInstanceHandler;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private VariableTemplate variableTemplate;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  private Long initialBatchOperationMaxSize;

  @Autowired private UserTaskReader userTaskReader;

  @Override
  @Before
  public void before() {
    super.before();

    migrateProcessInstanceHandler.setZeebeClient(super.getClient());
    mockMvc = mockMvcTestRule.getMockMvc();
    initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
  }

  @Override
  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);

    super.after();
  }

  @Test
  public void testCanMigrateZeebeUserTask() throws Exception {
    final var processDefinitionKey =
        tester
            .deployProcess("three-zeebe-user-tasks.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .startProcessInstance("Three-Zeebe-User-Tasks")
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .userTasksAreCreated(3)
            .getProcessDefinitionKey();

    final var beforeUserTasks = userTaskReader.getUserTasks();
    final var userTask1 =
        beforeUserTasks.stream()
            .filter(u -> "UserTask-1".equals(u.getElementId()))
            .findFirst()
            .get();
    final var userTask3 =
        beforeUserTasks.stream()
            .filter(u -> "UserTask-3".equals(u.getElementId()))
            .findFirst()
            .get();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(String.valueOf(processDefinitionKey))
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("UserTask-1")
                                .setTargetElementId("UserTask-2"),
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("UserTask-2")
                                .setTargetElementId("UserTask-1"),
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("UserTask-3")
                                .setTargetElementId("UserTask-3"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // the state of operation is COMPLETED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    final var afterUserTasks = userTaskReader.getUserTasks();
    final var afterUserTask1 =
        afterUserTasks.stream()
            .filter(u -> "UserTask-1".equals(u.getElementId()))
            .findFirst()
            .get();
    assertThat(userTask1.getUserTaskKey()).isNotEqualTo(afterUserTask1.getUserTaskKey());
    final var afterUserTask3 =
        afterUserTasks.stream()
            .filter(u -> "UserTask-3".equals(u.getElementId()))
            .findFirst()
            .get();
    assertThat(userTask3.getUserTaskKey()).isEqualTo(afterUserTask3.getUserTaskKey());
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnProcessInstanceNotActive() throws Exception {

    // given
    // process instances that complete execution
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done();
    final Long processDefinitionKey = deployProcess(startEndProcess, "startEndProcess.bpmn");
    final Long processInstanceKey1 =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    final Long processInstanceKey2 =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey1);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey2);

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
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

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations)
        .extracting("batchOperationId")
        .containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations)
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.FAILED);
    assertThat(operations).extracting("errorMessage").doesNotContainNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnInvalidTargetProcessDefinition()
      throws Exception {

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

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(String.valueOf("123"))
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("task")
                                .setTargetElementId("task"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations)
        .extracting("batchOperationId")
        .containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations)
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.FAILED);
    assertThat(operations).extracting("errorMessage").doesNotContainNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnInvalidElements() throws Exception {

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

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(String.valueOf(processDefinitionKey))
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("source")
                                .setTargetElementId("target"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations)
        .extracting("batchOperationId")
        .containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations)
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.FAILED);
    assertThat(operations).extracting("errorMessage").doesNotContainNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnActiveElementsNotMapped() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 =
        tester
            .deployProcess("double-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processDefinitionKey2 =
        tester
            .deployProcess("demoProcess_v_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey =
        tester
            .startProcessInstance("demoProcess", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();

    final String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(String.valueOf(targetProcessDefinitionKey))
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("taskA")
                                .setTargetElementId("taskA"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());
    final OperationEntity operation = operations.get(0);

    assertThat(operations.size()).isEqualTo(1);
    assertThat(operation.getType()).isEqualTo(request.getOperationType());
    assertThat(operation.getBatchOperationId()).isEqualTo(batchOperationEntity.getId());
    assertThat(operation.getMigrationPlan()).isEqualTo(migrationPlanJson);
    assertThat(operation.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .contains(
            "no mapping instruction defined for active element with id 'taskD'. Elements cannot be migrated without a mapping.");
  }

  @Test
  public void testMigrateProcessInstanceShouldMigrateWhenSameProcessDefinition() throws Exception {

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

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(String.valueOf(processDefinitionKey))
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("task")
                                .setTargetElementId("task"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // the state of operation is COMPLETED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations)
        .extracting("batchOperationId")
        .containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations)
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.COMPLETED);
    assertThat(operations).extracting("errorMessage").containsOnlyNulls();
  }

  @Test
  public void testMigrateProcessInstanceShouldMigrateWhenDifferentProcessDefinition()
      throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 =
        tester
            .deployProcess("double-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("doubleTask", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processDefinitionKey2 =
        tester
            .deployProcess("demoProcess_v_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();

    final String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query =
        createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("taskA")
                                .setTargetElementId("taskA"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // the state of operation is COMPLETED
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());
    final OperationEntity operation = operations.get(0);

    assertThat(operations.size()).isEqualTo(1);
    assertThat(operation.getType()).isEqualTo(request.getOperationType());
    assertThat(operation.getBatchOperationId()).isEqualTo(batchOperationEntity.getId());
    assertThat(operation.getMigrationPlan()).isEqualTo(migrationPlanJson);
    assertThat(operation.getProcessInstanceKey()).isEqualTo(processInstanceKey1);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getErrorMessage()).isNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldImportMigratedProcessInstance() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 =
        tester
            .deployProcess("double-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("doubleTask", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processDefinitionKey2 =
        tester
            .deployProcess("demoProcess_v_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();

    final String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query =
        createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("taskA")
                                .setTargetElementId("taskA"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // the process instance is migrated
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);

    final List<ListViewProcessInstanceDto> migratedPIs =
        tester.getProcessInstanceByIds(List.of(processInstanceKey1));
    final ListViewProcessInstanceDto migratedPI = migratedPIs.get(0);

    assertThat(migratedPIs).size().isEqualTo(1);
    assertThat(migratedPI.getProcessId()).isEqualTo(targetProcessDefinitionKey);
    assertThat(migratedPI.getProcessName()).isEqualTo("Demo process");
    assertThat(migratedPI.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(migratedPI.getOperations().size()).isEqualTo(1);
    assertThat(migratedPI.getOperations().get(0).getBatchOperationId())
        .isEqualTo(batchOperationEntity.getId());
    assertThat(migratedPI.getOperations().get(0).getType())
        .isEqualTo(OperationType.MIGRATE_PROCESS_INSTANCE);
    assertThat(migratedPI.getOperations().get(0).getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void testMigrateProcessInstanceShouldImportMigratedFlowNodes() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 =
        tester
            .deployProcess("double-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("doubleTask", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processDefinitionKey2 =
        tester
            .deployProcess("demoProcess_v_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();

    final String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query =
        createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("taskA")
                                .setTargetElementId("taskA"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // the flow node is migrated
    final List<FlowNodeInstanceEntity> flowNodes =
        searchAllDocuments(flowNodeInstanceTemplate.getAlias(), FlowNodeInstanceEntity.class);
    final FlowNodeInstanceEntity migratedFlowNode =
        flowNodes.stream()
            .filter(
                x ->
                    (Objects.equals(x.getProcessInstanceKey(), processInstanceKey1)
                        && "taskA".equals(x.getFlowNodeId())))
            .findFirst()
            .orElseThrow();

    assertThat(migratedFlowNode.getProcessDefinitionKey()).isEqualTo(processDefinitionKey2);
    assertThat(migratedFlowNode.getBpmnProcessId()).isEqualTo("demoProcess");
  }

  @Test
  public void testMigrateSubprocessToSubprocess() throws Exception {
    // given
    // process instances that are running
    final var processDefinitionFrom =
        tester
            .deployProcess("migration-subprocess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final var processFrom =
        tester
            .startProcessInstance("prWithSubprocess", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .then()
            .completeTask("taskA")
            .and()
            .waitUntil()
            .flowNodeIsActive("subprocess")
            .then()
            .getProcessInstanceKey();
    final var processDefinitionTo =
        tester
            .deployProcess("migration-subprocess2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    // when
    // execute MIGRATE_PROCESS_INSTANCE operation
    final var query = createGetProcessInstancesByIdsQuery(List.of(processFrom));
    final var request =
        new CreateBatchOperationRequestDto()
            .setName("migrate process with subprocesses")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(String.valueOf(processDefinitionTo))
                    .setMappingInstructions(
                        List.of(
                            new MappingInstruction()
                                .setSourceElementId("taskA")
                                .setTargetElementId("taskA"),
                            new MappingInstruction()
                                .setSourceElementId("subprocess")
                                .setTargetElementId("subprocess2"),
                            new MappingInstruction()
                                .setSourceElementId("innerSubprocess")
                                .setTargetElementId("innerSubprocess2"),
                            new MappingInstruction()
                                .setSourceElementId("taskB")
                                .setTargetElementId("taskB"))));

    final var mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // subprocesses are migrated
    final var subprocessFlowNodes =
        searchAllDocuments(flowNodeInstanceTemplate.getAlias(), FlowNodeInstanceEntity.class)
            .stream()
            .filter(fn -> fn.getType().equals(FlowNodeType.SUB_PROCESS))
            .toList();

    assertThat(subprocessFlowNodes).hasSize(2);
    assertMigratedFieldsByFlowNodeId(
        subprocessFlowNodes, "subprocess2", processFrom, processDefinitionTo, "prWithSubprocess2");
    assertMigratedFieldsByFlowNodeId(
        subprocessFlowNodes,
        "innerSubprocess2",
        processFrom,
        processDefinitionTo,
        "prWithSubprocess2");
  }

  private void assertMigratedFieldsByFlowNodeId(
      final List<FlowNodeInstanceEntity> candidates,
      final String flowNodeId,
      final Long instanceKey,
      final Long processDefinitionTo,
      final String bpmnProcessId) {
    final var flowNode =
        candidates.stream()
            .filter(fn -> fn.getFlowNodeId().equals(flowNodeId))
            .findFirst()
            .orElseThrow();
    assertThat(flowNode.getProcessInstanceKey()).isEqualTo(instanceKey);
    assertThat(flowNode.getProcessDefinitionKey()).isEqualTo(processDefinitionTo);
    assertThat(flowNode.getBpmnProcessId()).isEqualTo(bpmnProcessId);
  }

  @Test
  public void testMigrateProcessInstanceShouldImportMigratedVariables() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 =
        tester
            .deployProcess("double-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final String payload = "{\"aaa\":\"yoyo\",\"bbb\":null}";
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("doubleTask", payload)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processDefinitionKey2 =
        tester
            .deployProcess("demoProcess_v_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();

    final String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    // when
    // we call MIGRATE_PROCESS_INSTANCE operation
    final ListViewQueryDto query =
        createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("taskA")
                                .setTargetElementId("taskA"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    // and execute the operation
    tester.waitUntil().operationIsCompleted();

    // then
    // the variable is migrated
    final List<VariableEntity> variables =
        searchAllDocuments(variableTemplate.getAlias(), VariableEntity.class);
    final List<VariableEntity> migratedVariables =
        variables.stream().filter(x -> List.of("aaa", "bbb").contains(x.getName())).toList();

    assertThat(migratedVariables).size().isEqualTo(2);
    assertThat(migratedVariables)
        .extracting("processInstanceKey")
        .containsOnly(processInstanceKey1);
    assertThat(migratedVariables)
        .extracting("processDefinitionKey")
        .containsOnly(processDefinitionKey2);
    assertThat(migratedVariables).extracting("bpmnProcessId").containsOnly("demoProcess");
  }
}
