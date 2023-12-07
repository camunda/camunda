/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.zeebe.operation.*;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

import java.util.List;
import java.util.Objects;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class MigrateProcessInstanceOperationIT extends OperateZeebeAbstractIT {

  @Autowired
  private MigrateProcessInstanceHandler migrateProcessInstanceHandler;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  private Long initialBatchOperationMaxSize;

  @Before
  public void before() {
    super.before();

    migrateProcessInstanceHandler.setZeebeClient(super.getClient());
    mockMvc = mockMvcTestRule.getMockMvc();
    initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
  }

  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);

    super.after();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnProcessInstanceNotActive() throws Exception {

    // given
    // process instances that complete execution
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .endEvent()
            .done();
    final Long processDefinitionKey = deployProcess(startEndProcess, "startEndProcess.bpmn");
    final Long processInstanceKey1 = ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    final Long processInstanceKey2 = ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey1);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey2);

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey("123").setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("source").setTargetElementId("target"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    executeOneBatch();
    sleepFor(2000);

    //then
    //the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations = searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations).extracting("batchOperationId").containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations).extracting("processInstanceKey").containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.FAILED);
    assertThat(operations).extracting("errorMessage").doesNotContainNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnInvalidTargetProcessDefinition() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey = tester
        .deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey1 = tester.startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processInstanceKey2 = tester.startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(String.valueOf("123")).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("task").setTargetElementId("task"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    executeOneBatch();
    sleepFor(2000);

    //then
    //the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations = searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations).extracting("batchOperationId").containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations).extracting("processInstanceKey").containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.FAILED);
    assertThat(operations).extracting("errorMessage").doesNotContainNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnInvalidElements() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey = tester
        .deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey1 = tester.startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processInstanceKey2 = tester.startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(String.valueOf(processDefinitionKey)).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("source").setTargetElementId("target"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    executeOneBatch();
    sleepFor(2000);

    //then
    //the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations = searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations).extracting("batchOperationId").containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations).extracting("processInstanceKey").containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.FAILED);
    assertThat(operations).extracting("errorMessage").doesNotContainNull();
  }

  @Test
  public void testMigrateProcessInstanceShouldFailOnActiveElementsNotMapped() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 = tester
        .deployProcess("double-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processDefinitionKey2 = tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey = tester.startProcessInstance("demoProcess", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();

    String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(String.valueOf(targetProcessDefinitionKey)).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("taskA").setTargetElementId("taskA"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    executeOneBatch();
    sleepFor(2000);

    //then
    //the state of operation is FAILED
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations = searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());
    final OperationEntity operation = operations.get(0);

    assertThat(operations.size()).isEqualTo(1);
    assertThat(operation.getType()).isEqualTo(request.getOperationType());
    assertThat(operation.getBatchOperationId()).isEqualTo(batchOperationEntity.getId());
    assertThat(operation.getMigrationPlan()).isEqualTo(migrationPlanJson);
    assertThat(operation.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).contains("no mapping instruction defined for active element with id 'taskD'. Elements cannot be migrated without a mapping.");
  }

  @Test
  public void testMigrateProcessInstanceShouldMigrateWhenSameProcessDefinition() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey = tester
        .deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey1 = tester.startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processInstanceKey2 = tester.startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetAllProcessInstancesQuery();
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(String.valueOf(processDefinitionKey)).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("task").setTargetElementId("task"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    tester.waitUntil().operationIsCompleted();

    //then
    //the state of operation is COMPLETED
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations = searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations).extracting("type").containsOnly(request.getOperationType());
    assertThat(operations).extracting("batchOperationId").containsOnly(batchOperationEntity.getId());
    assertThat(operations).extracting("migrationPlan").containsOnly(migrationPlanJson);
    assertThat(operations).extracting("processInstanceKey").containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
    assertThat(operations).extracting("state").containsOnly(OperationState.COMPLETED);
    assertThat(operations).extracting("errorMessage").containsOnlyNulls();
  }

  @Test
  public void testMigrateProcessInstanceShouldMigrateWhenDifferentProcessDefinition() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 = tester
        .deployProcess("double-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey1 = tester.startProcessInstance("doubleTask", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processDefinitionKey2 = tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();

    String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(targetProcessDefinitionKey).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("taskA").setTargetElementId("taskA"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    tester.waitUntil().operationIsCompleted();

    //then
    //the state of operation is COMPLETED
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations = searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
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
    final Long processDefinitionKey1 = tester
        .deployProcess("double-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey1 = tester.startProcessInstance("doubleTask", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processDefinitionKey2 = tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();

    String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(targetProcessDefinitionKey).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("taskA").setTargetElementId("taskA"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    tester.waitUntil().operationIsCompleted();

    //then
    //the process instance is migrated
    final BatchOperationEntity batchOperationEntity = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);

    List<ListViewProcessInstanceDto> migratedPIs = tester.getProcessInstanceByIds(List.of(processInstanceKey1));
    ListViewProcessInstanceDto migratedPI = migratedPIs.get(0);

    assertThat(migratedPIs).size().isEqualTo(1);
    assertThat(migratedPI.getProcessId()).isEqualTo(targetProcessDefinitionKey);
    assertThat(migratedPI.getProcessName()).isEqualTo("Demo process");
    assertThat(migratedPI.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(migratedPI.getOperations().size()).isEqualTo(1);
    assertThat(migratedPI.getOperations().get(0).getBatchOperationId()).isEqualTo(batchOperationEntity.getId());
    assertThat(migratedPI.getOperations().get(0).getType()).isEqualTo(OperationType.MIGRATE_PROCESS_INSTANCE);
    assertThat(migratedPI.getOperations().get(0).getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void testMigrateProcessInstanceShouldImportMigratedFlowNodes() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 = tester
        .deployProcess("double-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final Long processInstanceKey1 = tester.startProcessInstance("doubleTask", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processDefinitionKey2 = tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();

    String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(targetProcessDefinitionKey).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("taskA").setTargetElementId("taskA"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    tester.waitUntil().operationIsCompleted();

    //then
    //the flow node is migrated
    List<FlowNodeInstanceEntity> flowNodes = searchAllDocuments(flowNodeInstanceTemplate.getAlias(), FlowNodeInstanceEntity.class);
    FlowNodeInstanceEntity migratedFlowNode = flowNodes.stream().filter(x -> (Objects.equals(x.getProcessInstanceKey(), processInstanceKey1) && "taskA".equals(x.getFlowNodeId()))).findFirst().orElseThrow();

    assertThat(migratedFlowNode.getProcessDefinitionKey()).isEqualTo(processDefinitionKey2);
    assertThat(migratedFlowNode.getBpmnProcessId()).isEqualTo("demoProcess");
  }

  @Test
  public void testMigrateProcessInstanceShouldImportMigratedVariables() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey1 = tester
        .deployProcess("double-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    final String payload = "{\"aaa\":\"yoyo\",\"bbb\":null}";
    final Long processInstanceKey1 = tester.startProcessInstance("doubleTask", payload)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    final Long processDefinitionKey2 = tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();

    String targetProcessDefinitionKey = String.valueOf(processDefinitionKey2);

    //when
    //we call MIGRATE_PROCESS_INSTANCE operation
    ListViewQueryDto query = createGetProcessInstancesByIdsQuery(List.of(processInstanceKey1));
    CreateBatchOperationRequestDto request = new CreateBatchOperationRequestDto()
        .setName("batch-1")
        .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE).setQuery(query)
        .setMigrationPlan(new MigrationPlanDto().setTargetProcessDefinitionKey(targetProcessDefinitionKey).setMappingInstructions(List.of(
            new MigrationPlanDto.MappingInstruction().setSourceElementId("taskA").setTargetElementId("taskA"))));

    MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);
    //and execute the operation
    tester.waitUntil().operationIsCompleted();

    //then
    //the variable is migrated
    List<VariableEntity> variables = searchAllDocuments(variableTemplate.getAlias(), VariableEntity.class);
    List<VariableEntity> migratedVariables = variables.stream().filter(x -> List.of("aaa", "bbb").contains(x.getName())).toList();

    assertThat(migratedVariables).size().isEqualTo(2);
    assertThat(migratedVariables).extracting("processInstanceKey").containsOnly(processInstanceKey1);
    assertThat(migratedVariables).extracting("processDefinitionKey").containsOnly(processDefinitionKey2);
    assertThat(migratedVariables).extracting("bpmnProcessId").containsOnly("demoProcess");
  }
}
