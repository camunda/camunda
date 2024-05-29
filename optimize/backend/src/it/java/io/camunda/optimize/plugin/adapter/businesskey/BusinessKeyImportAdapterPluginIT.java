/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.plugin.adapter.businesskey;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractPlatformIT;
import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.BpmnModels;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class BusinessKeyImportAdapterPluginIT extends AbstractPlatformIT {
  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void businessKeysAreAdaptedByPluginOnRunningProcessInstanceImport() {
    // given
    addBusinessKeyImportPluginBasePackagesToConfiguration(
        "io.camunda.optimize.testplugin.adapter.businesskey");

    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<String> processInstanceBusinessKeys = getBusinessKeysForAllImportedProcessInstances();

    // then
    assertThat(processInstanceBusinessKeys).hasSize(2).allMatch(key -> key.equals("foo"));
  }

  @Test
  public void businessKeysAreAdaptedByPluginOnCompletedProcessInstanceImport() {
    // given
    addBusinessKeyImportPluginBasePackagesToConfiguration(
        "io.camunda.optimize.testplugin.adapter.businesskey");

    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto processInstance1 =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto processInstance2 =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    importAllEngineEntitiesFromScratch();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());

    importAllEngineEntitiesFromLastIndex();

    // when
    List<String> processInstanceBusinessKeys = getBusinessKeysForAllImportedProcessInstances();

    // then
    assertThat(processInstanceBusinessKeys).hasSize(2).allMatch(key -> key.equals("foo"));
  }

  @Test
  public void nonExistentAdapterDoesNotStopImportProcess() {
    // given
    addBusinessKeyImportPluginBasePackagesToConfiguration("foo.bar");
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<String> processInstanceBusinessKeys = getBusinessKeysForAllImportedProcessInstances();

    // then
    assertThat(processInstanceBusinessKeys).hasSize(2);
  }

  private void addBusinessKeyImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setBusinessKeyImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private ProcessDefinitionEngineDto deployUserTaskProcess() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        BpmnModels.getSingleUserTaskDiagram());
  }

  private List<String> getBusinessKeysForAllImportedProcessInstances() {
    return databaseIntegrationTestExtension.getAllProcessInstances().stream()
        .map(ProcessInstanceDto::getBusinessKey)
        .toList();
  }
}
