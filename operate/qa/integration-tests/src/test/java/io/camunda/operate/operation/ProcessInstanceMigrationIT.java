/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.operation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto.MappingInstruction;
import io.camunda.operate.webapp.zeebe.operation.MigrateProcessInstanceHandler;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

class ProcessInstanceMigrationIT extends OperateZeebeSearchAbstractIT {

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private MigrateProcessInstanceHandler migrateProcessInstanceHandler;

  @Disabled("To be re-enabled with the fix in https://github.com/camunda/camunda/issues/24084")
  void shouldMigrateSubprocessToSubprocess() throws Exception {
    // given
    // process instances that are running
    final var processDefinitionFrom =
        operateTester.deployProcessAndWait("migration-subprocess.bpmn");
    final var processFrom = operateTester.startProcessAndWait("prWithSubprocess");
    operateTester.completeJob("taskA").waitForFlowNodeActive(processFrom, "subprocess");

    final var processDefinitionTo =
        operateTester.deployProcessAndWait("migration-subprocess2.bpmn");
    // when
    // execute MIGRATE_PROCESS_INSTANCE
    final var migrationPlan =
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
                        .setTargetElementId("taskB")));
    migrateProcessInstanceHandler.setCamundaClient(zeebeContainerManager.getClient());
    migrateProcessInstanceHandler.migrate(
        processFrom, migrationPlan, String.valueOf(UUID.randomUUID().getMostSignificantBits()));

    // then
    // subprocesses are migrated
    operateTester.waitForFlowNodeActive(processFrom, "subprocess2");
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

  protected <R> List<R> searchAllDocuments(final String index, final Class<R> clazz) {
    try {
      return testSearchRepository.searchAll(index, clazz);
    } catch (final IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
  }
}
