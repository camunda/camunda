/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.processdefinition;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.ZeebeBpmnModels.END_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;

public class ZeebeProcessDefinitionImportIT extends AbstractZeebeIT {

  @Test
  public void importZeebeProcess_allDataSavedToDefinition() {
    // given
    final String processName = "someProcess";
    final BpmnModelInstance simpleProcess = createSimpleServiceTaskProcess(processName);
    final Process deployedProcess = deployProcessAndStartInstance(simpleProcess);
    waitUntilNumberOfDefinitionsExported(1);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions())
      .singleElement()
      .satisfies(importedDef -> {
        assertThat(importedDef.getId()).isEqualTo(String.valueOf(deployedProcess.getProcessDefinitionKey()));
        assertThat(importedDef.getKey()).isEqualTo(deployedProcess.getBpmnProcessId());
        assertThat(importedDef.getVersion()).isEqualTo(String.valueOf(deployedProcess.getVersion()));
        assertThat(importedDef.getVersionTag()).isNull();
        assertThat(importedDef.getType()).isEqualTo(DefinitionType.PROCESS);
        assertThat(importedDef.isEventBased()).isFalse();
        assertThat(importedDef.getBpmn20Xml()).isEqualTo(Bpmn.convertToString(simpleProcess));
        assertThat(importedDef.getName()).isEqualTo(processName);
        assertThat(importedDef.getDataSource().getType()).isEqualTo(DataImportSourceType.ZEEBE);
        assertThat(importedDef.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(importedDef.getTenantId()).isNull();
        assertThat(importedDef.isDeleted()).isFalse();
        assertThat(importedDef.getUserTaskNames()).isEmpty();
        assertThat(importedDef.getFlowNodeData()).containsExactlyInAnyOrder(
          new FlowNodeDataDto(START_EVENT, START_EVENT, "startEvent"),
          new FlowNodeDataDto(SERVICE_TASK, SERVICE_TASK, "serviceTask"),
          new FlowNodeDataDto(END_EVENT, null, "endEvent")
        );
      });
  }

  @Test
  public void importZeebeProcess_unnamedProcessUsesProcessIdAsName() {
    // given
    final BpmnModelInstance noNameStartEventProcess = Bpmn.createExecutableProcess()
      .startEvent(START_EVENT).name(START_EVENT).done();
    final Process deployedProcess = deployProcessAndStartInstance(noNameStartEventProcess);
    waitUntilNumberOfDefinitionsExported(1);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions())
      .singleElement()
      .satisfies(importedDef -> {
        assertThat(importedDef.getId()).isEqualTo(String.valueOf(deployedProcess.getProcessDefinitionKey()));
        assertThat(importedDef.getKey()).isEqualTo(deployedProcess.getBpmnProcessId());
        assertThat(importedDef.getVersion()).isEqualTo(String.valueOf(deployedProcess.getVersion()));
        assertThat(importedDef.getVersionTag()).isNull();
        assertThat(importedDef.getType()).isEqualTo(DefinitionType.PROCESS);
        assertThat(importedDef.isEventBased()).isFalse();
        assertThat(importedDef.getBpmn20Xml()).isEqualTo(Bpmn.convertToString(noNameStartEventProcess));
        assertThat(importedDef.getName()).isEqualTo(deployedProcess.getBpmnProcessId());
        assertThat(importedDef.getDataSource().getType()).isEqualTo(DataImportSourceType.ZEEBE);
        assertThat(importedDef.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(importedDef.getTenantId()).isNull();
        assertThat(importedDef.isDeleted()).isFalse();
        assertThat(importedDef.getUserTaskNames()).isEmpty();
        assertThat(importedDef.getFlowNodeData()).containsExactlyInAnyOrder(
          new FlowNodeDataDto(START_EVENT, START_EVENT, "startEvent")
        );
      });
  }

  @Test
  public void importZeebeProcess_multipleProcessesDeployed() {
    // given
    final String firstProcessName = "firstProcess";
    deployProcessAndStartInstance(createSimpleServiceTaskProcess(firstProcessName));
    final String secondProcessName = "secondProcess";
    deployProcessAndStartInstance(createSimpleServiceTaskProcess(secondProcessName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getName)
      .containsExactlyInAnyOrder(firstProcessName, secondProcessName);
  }

  @Test
  @SneakyThrows
  public void importZeebeProcess_multipleProcessesDeployedOnDifferentDays() {
    // given
    final String firstProcessName = "firstProcess";
    deployProcessAndStartInstance(createSimpleServiceTaskProcess(firstProcessName));

    zeebeExtension.setClock(Instant.now().plus(1, ChronoUnit.DAYS));
    final String secondProcessName = "secondProcess";
    deployProcessAndStartInstance(createSimpleServiceTaskProcess(secondProcessName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getName)
      .containsExactlyInAnyOrder(firstProcessName, secondProcessName);
  }

  @Test
  public void importZeebeProcess_multipleVersionsOfSameProcess() {
    // given
    final String processName = "someProcess";
    final Process firstVersion = deployProcessAndStartInstance(createSimpleServiceTaskProcess(processName));
    final Process secondVersion = deployProcessAndStartInstance(createStartEndProcess(processName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::getVersion)
      .containsExactlyInAnyOrder(
        Tuple.tuple(String.valueOf(firstVersion.getProcessDefinitionKey()), String.valueOf(firstVersion.getVersion())),
        Tuple.tuple(String.valueOf(secondVersion.getProcessDefinitionKey()), String.valueOf(secondVersion.getVersion()))
      );
  }

  @Test
  public void importZeebeProcess_multipleProcessOverMultipleBatches() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();

    final String firstProcessName = "firstProcess";
    deployProcessAndStartInstance(createSimpleServiceTaskProcess(firstProcessName));
    final String secondProcessName = "secondProcess";
    deployProcessAndStartInstance(createSimpleServiceTaskProcess(secondProcessName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(1)
      .extracting(DefinitionOptimizeResponseDto::getName).containsExactlyInAnyOrder(firstProcessName);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getName)
      .containsExactlyInAnyOrder(firstProcessName, secondProcessName);
  }

  // Elements such as data stores, date objects, link events, escalation events and undefined tasks were introduced with 8.2
  @DisabledIf("isZeebeVersionPre82")
  @Test
  public void importZeebeProcess_processContainsNewBpmnElementsIntroducedWith820() {
    // given a process that contains the following:
    // data stores, date objects, link events, escalation events, undefined tasks
    final BpmnModelInstance model = readProcessDiagramAsInstance("/bpmn/compatibility/adventure.bpmn");
    final String processId = zeebeExtension.deployProcess(model).getBpmnProcessId();
    zeebeExtension.startProcessInstanceWithVariables(
      processId,
      Map.of("space", true, "time", true)
    );

    // when
    waitUntilNumberOfDefinitionsExported(1);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions())
      .singleElement()
      .extracting(ProcessDefinitionOptimizeDto::getFlowNodeData)
      .satisfies(flowNodeDataDtos -> assertThat(flowNodeDataDtos).extracting(FlowNodeDataDto::getId)
        .contains(
          "signalStartEventId",
          "linkIntermediateThrowEventId",
          "linkIntermediateCatchEventId",
          "undefinedTaskId",
          "escalationIntermediateThrowEventId",
          "escalationNonInterruptingBoundaryEventId",
          "escalationBoundaryEventId",
          "escalationNonInterruptingStartEventId",
          "escalationStartEventId",
          "escalationEndEventId"
        ));
  }

  private Process deployProcessAndStartInstance(final BpmnModelInstance simpleProcess) {
    final Process deployedProcess = zeebeExtension.deployProcess(simpleProcess);
    zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
    return deployedProcess;
  }

}
