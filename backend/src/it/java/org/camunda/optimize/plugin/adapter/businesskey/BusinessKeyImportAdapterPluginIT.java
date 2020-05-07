/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.adapter.businesskey;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class BusinessKeyImportAdapterPluginIT extends AbstractIT {
  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void businessKeysAreAdaptedByPluginOnRunningProcessInstanceImport() {
    // given
    addBusinessKeyImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.businesskey");

    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

  // when
  List<String> processInstanceBusinessKeys = getBusinessKeysForAllImportedProcessInstances();

  // then
  assertThat(processInstanceBusinessKeys)
      .hasSize(2)
      .allMatch(key -> key.equals("foo"));
}


  @Test
  public void businessKeysAreAdaptedByPluginOnCompletedProcessInstanceImport() {
    // given
    addBusinessKeyImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.businesskey");

    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());

    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> processInstanceBusinessKeys = getBusinessKeysForAllImportedProcessInstances();

    // then
    assertThat(processInstanceBusinessKeys)
      .hasSize(2)
      .allMatch(key -> key.equals("foo"));
  }

  @Test
  public void nonExistentAdapterDoesNotStopImportProcess() {
    // given
    addBusinessKeyImportPluginBasePackagesToConfiguration("foo.bar");
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  private List<String> getBusinessKeysForAllImportedProcessInstances(){
    return elasticSearchIntegrationTestExtension.getAllProcessInstances()
      .stream()
      .map(instance -> instance.getBusinessKey())
      .collect(toList());
  }
}
