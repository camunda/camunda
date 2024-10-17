/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.client.api.command.MigrationPlanImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MigratedIncidentZeebeImportIT extends OperateZeebeSearchAbstractIT {

  @Autowired private IncidentTemplate incidentTemplate;

  @Test
  public void shouldImportMigratedIncident() throws IOException {

    // given
    final String bpmnSource = "double-task-incident.bpmn";
    final String bpmnTarget = "double-task.bpmn";
    final Long processDefinitionKeySource = operateTester.deployProcessAndWait(bpmnSource);
    final Long processDefinitionKeyTarget = operateTester.deployProcessAndWait(bpmnTarget);
    final ZeebeClient zeebeClient = zeebeContainerManager.getClient();

    // when
    final Long processInstanceKey = operateTester.startProcessAndWait("doubleTaskIncident");
    operateTester.waitUntilIncidentsAreActive(processInstanceKey, 1);

    final MigrationPlan migrationPlan =
        new MigrationPlanImpl(processDefinitionKeyTarget, new ArrayList<>());
    List.of("taskA", "taskB")
        .forEach(
            item ->
                migrationPlan
                    .getMappingInstructions()
                    .add(new MigrationPlanBuilderImpl.MappingInstruction(item + "Incident", item)));
    zeebeClient
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(migrationPlan)
        .send()
        .join();

    operateTester.waitUntilIncidentsInProcessAreActive("doubleTask", 1);
    final List<IncidentEntity> incidents =
        testSearchRepository.searchTerm(
            incidentTemplate.getAlias(),
            IncidentTemplate.PROCESS_INSTANCE_KEY,
            processInstanceKey,
            IncidentEntity.class,
            1);

    // then
    assertThat(incidents.size()).isEqualTo(1);
    assertThat(incidents.get(0).getState()).isEqualTo(IncidentState.ACTIVE);
    assertThat(incidents.get(0).getBpmnProcessId()).isEqualTo("doubleTask");
    assertThat(incidents.get(0).getProcessDefinitionKey()).isEqualTo(processDefinitionKeyTarget);
    assertThat(incidents.get(0).getFlowNodeId()).isEqualTo("taskA");
  }
}
