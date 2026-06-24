/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

public class ZeebeVariableUpdateImportIT extends AbstractCCSMIT {

  private final String PROCESS_ID = "demoProcess";
  private final Map<String, Object> VARIABLES = generateVariables();
  private final Map<String, Object> UPDATED_VARIABLES = generateUpdatedVariables();

  @Test
  public void
      zeebeVariableImport_importRecordsForTheCreationAndTheUpdateOfProcessVariablesOnSameBatch() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    zeebeExtension.addVariablesToScope(processInstanceKey, UPDATED_VARIABLES, true);
    waitUntilDefinitionWithIdExported(PROCESS_ID);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        5, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void
      zeebeVariableImport_importRecordsForTheCreationAndTheUpdateOfProcessVariablesOnDifferentBatch() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        5, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey, UPDATED_VARIABLES, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        5, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void zeebeVariableImport_updateVariablesForInstancesInSeveralDefinitions() {
    // given
    final long processInstanceKey1 = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    zeebeExtension.addVariablesToScope(processInstanceKey1, UPDATED_VARIABLES, true);

    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long processInstanceKey2 =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), VARIABLES);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        5, processInstanceKey1);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        5, processInstanceKey2);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey2, UPDATED_VARIABLES, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        5, processInstanceKey1);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        5, processInstanceKey2);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto savedProcessInstance1 =
        getProcessInstanceForId(String.valueOf(processInstanceKey1));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance1);
    final ProcessInstanceDto savedProcessInstance2 =
        getProcessInstanceForId(String.valueOf(processInstanceKey2));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance2);
  }

  @Test
  public void zeebeVariableImport_updateVariableValueWithNullGetsIgnored() {
    // given
    final long processInstanceKey =
        deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(1, processInstanceKey);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        2, processInstanceKey);
    importAllZeebeEntitiesFromLastIndex();
    final Map<String, Object> newVariables = new HashMap<>();
    newVariables.put("var1", null);
    zeebeExtension.addVariablesToScope(processInstanceKey, newVariables, true);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), newVariables, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        2, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE),
            Tuple.tuple(
                "var1", Collections.singletonList("flowNodeInstanceScopeValue"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_variableNameOnSeveralScopesOnlyProcessLevelGetsUpdated() {
    // given
    final long processInstanceKey =
        deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        2, processInstanceKey);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
        processInstanceKey, Map.of("var1", "processInstanceScopeUpdatedValue"), true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "var1", Collections.singletonList("processInstanceScopeUpdatedValue"), STRING_TYPE),
            Tuple.tuple(
                "var1", Collections.singletonList("flowNodeInstanceScopeValue"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_variableNameOnSeveralScopesOnlyFlowNodeLevelGetsUpdated() {
    // given
    final long processInstanceKey =
        deployProcessAndStartProcessInstanceWithVariables(
            Map.of("var1", "processInstanceScopeValue"));
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(1, processInstanceKey);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        2, processInstanceKey);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeUpdatedValue"), true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "var1", Collections.singletonList("processInstanceScopeValue"), STRING_TYPE),
            Tuple.tuple(
                "var1",
                Collections.singletonList("flowNodeInstanceScopeUpdatedValue"),
                STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_updateTheTypeOfVariables() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        5, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(
        processInstanceKey,
        Map.of("var1", false, "var2", "someValue", "var3", "", "var4", true, "var5", 123.0),
        true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        5, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("false"), BOOLEAN_TYPE),
            Tuple.tuple("var2", Collections.singletonList("someValue"), STRING_TYPE),
            Tuple.tuple("var3", Collections.singletonList(""), STRING_TYPE),
            Tuple.tuple("var4", Collections.singletonList("true"), BOOLEAN_TYPE),
            Tuple.tuple("var5", Collections.singletonList("123.0"), DOUBLE_TYPE));
  }

  @Test
  public void
      zeebeVariableImport_updateFlowNodeLevelVariableWithPropagationOnlyUpdatesFlowNodeVariable() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(
        4, processInstanceEvent.getProcessInstanceKey());
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(
        processInstanceEvent.getProcessInstanceKey(),
        Map.of("var1", "processInstanceScopeValue"),
        true);
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        2, processInstanceEvent.getProcessInstanceKey());
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "updatedValue"), false);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        1, processInstanceEvent.getProcessInstanceKey());

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "var1", Collections.singletonList("processInstanceScopeValue"), STRING_TYPE),
            Tuple.tuple("var1", Collections.singletonList("updatedValue"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_updateVariableSeveralTimesInSameBatch() {
    // given
    final long processInstanceKey =
        deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "firstUpdate"), true);
    zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "secondUpdate"), true);
    importAllZeebeEntitiesFromLastIndex();
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        2, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("secondUpdate"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_updateVariableSeveralTimesInSeveralBatches() {
    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    final long processInstanceKey =
        deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "firstUpdate"), true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "secondUpdate"), true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        2, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("secondUpdate"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_updateObjectVariable() {
    // given
    final Map<String, Object> objectVar = new HashMap<>();
    objectVar.put("name", "Pond");
    objectVar.put("age", 28);
    objectVar.put("likes", List.of("optimize", "garlic"));
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVar);
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(variables);
    waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
        1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();

    objectVar.put("age", 29);
    objectVar.put("likes", List.of("optimize", "garlic", "tofu"));
    zeebeExtension.addVariablesToScope(processInstanceKey, variables, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
        1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "objectVar",
                OBJECT.getId(),
                Collections.singletonList(
                    variablesClient.createMapJsonObjectVariableDto(objectVar).getValue())),
            Tuple.tuple("objectVar.name", STRING.getId(), Collections.singletonList("Pond")),
            Tuple.tuple("objectVar.age", DOUBLE.getId(), Collections.singletonList("29.0")),
            Tuple.tuple("objectVar.likes", STRING.getId(), List.of("optimize", "garlic", "tofu")),
            Tuple.tuple("objectVar.likes._listSize", LONG.getId(), Collections.singletonList("3")));
  }

  private Map<String, Object> generateVariables() {
    return Map.of("var1", "someValue", "var2", false, "var3", 123, "var4", 123.3, "var5", "");
  }

  private Map<String, Object> generateUpdatedVariables() {
    return Map.of(
        "var1",
        "var1UpdatedValue",
        "var2",
        true,
        "var3",
        123.0,
        "var4",
        123,
        "var5",
        "var5UpdatedValue");
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
                    "No process instance with id " + processInstanceId + "found"));
  }

  private void waitUntilMinimumVariableDocumentsWithCreatedIntentForInstanceExportedCount(
      final int minExportedEventCount, final long processInstanceKey) {
    waitUntilMinimumVariableDocumentsWithIntentForInstanceExportedCount(
        minExportedEventCount, VariableIntent.CREATED, processInstanceKey);
  }

  private void waitUntilMinimumVariableDocumentsWithUpdatedIntentForInstanceExportedCount(
      final int minExportedEventCount, final long processInstanceKey) {
    waitUntilMinimumVariableDocumentsWithIntentForInstanceExportedCount(
        minExportedEventCount, VariableIntent.UPDATED, processInstanceKey);
  }

  private void assertThatVariablesHaveBeenImportedForProcessInstance(
      final ProcessInstanceDto processInstanceDto) {
    assertThat(processInstanceDto.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("var1UpdatedValue"), STRING_TYPE),
            Tuple.tuple("var2", Collections.singletonList("true"), BOOLEAN_TYPE),
            Tuple.tuple("var3", Collections.singletonList("123.0"), DOUBLE_TYPE),
            Tuple.tuple("var4", Collections.singletonList("123"), DOUBLE_TYPE),
            Tuple.tuple("var5", Collections.singletonList("var5UpdatedValue"), STRING_TYPE));
  }

  private long deployProcessAndStartProcessInstanceWithVariables(
      final Map<String, Object> variablesToAdd) {
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceWithVariables(
        deployedProcess.getBpmnProcessId(), variablesToAdd);
  }

  private ProcessInstanceEvent deployProcessAndStartProcessInstance() {
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }
}
