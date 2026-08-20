/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.Process;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("variable-import-disabled")
public class ZeebeVariableImportDisabledIT extends AbstractCCSMIT {

  private static final String PROCESS_ID = "demoProcess";
  private static final Map<String, Object> BASIC_VARIABLES =
      Map.of("var1", "someValue", "var2", false, "var3", 123, "var4", 123.3, "var5", "");

  @Test
  public void zeebeVariableImport_processStartedWithVariables() {
    // given
    final Long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables();

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesNotImported(savedProcessInstance);
  }

  private Long deployProcessAndStartProcessInstanceWithVariables() {
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceWithVariables(
        deployedProcess.getBpmnProcessId(), BASIC_VARIABLES);
  }

  private ProcessInstanceDto getProcessInstanceForId(final String processInstanceId) {
    return databaseIntegrationTestExtension.getAllProcessInstances().stream()
        .filter(instance -> instance.getProcessInstanceId().equals(processInstanceId))
        .collect(Collectors.toList())
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new OptimizeIntegrationTestException(
                    "No process instance with id " + processInstanceId + " found"));
  }

  private void assertThatVariablesNotImported(final ProcessInstanceDto processInstanceDto) {
    assertThat(processInstanceDto.getVariables()).hasSize(0);
  }
}
