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
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
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
      zeebeVariableImport_importRecordsForTheCreationAndTheUpdateOfProcessVariablesOnSameAndOnDifferentBatch() {
    // Covers two independent variable-update batching scenarios: scenario 1 imports creation and
    // update together in one batch; scenario 2 imports them as two separate batches. Each
    // scenario is reset to a clean Zeebe export index and clean Optimize data before it runs,
    // mirroring what @BeforeEach/@AfterEach would otherwise do between separate test methods.

    // given (same batch)
    {
      final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
      zeebeExtension.addVariablesToScope(processInstanceKey, UPDATED_VARIABLES, true);
      waitUntilNumberOfDefinitionsExported(1);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(5);

      // when (same batch)
      importAllZeebeEntitiesFromScratch();

      // then (same batch)
      final ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
      assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
    }

    // reset to a clean slate before the second scenario, mirroring @AfterEach/@BeforeEach
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // given (different batch)
    {
      final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(5);
      importAllZeebeEntitiesFromScratch();
      zeebeExtension.addVariablesToScope(processInstanceKey, UPDATED_VARIABLES, true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(5);

      // when (different batch)
      importAllZeebeEntitiesFromLastIndex();

      // then (different batch)
      final ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
      assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
    }
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
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(10);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey2, UPDATED_VARIABLES, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(10);

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
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();
    final Map<String, Object> newVariables = new HashMap<>();
    newVariables.put("var1", null);
    zeebeExtension.addVariablesToScope(processInstanceKey, newVariables, true);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), newVariables, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(2);

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
  public void
      zeebeVariableImport_variableNameOnSeveralScopesOnlyProcessLevelGetsUpdatedAndOnlyFlowNodeLevelGetsUpdated() {
    // Covers two independent scoped-update scenarios for a variable name that exists at both
    // process and flow-node scope: scenario 1 only the process-level value is updated; scenario
    // 2 only the flow-node-level value is updated. Each scenario is reset to a clean Zeebe export
    // index and clean Optimize data before it runs, mirroring what @BeforeEach/@AfterEach would
    // otherwise do between separate test methods.

    // given (only process level gets updated)
    {
      final long processInstanceKey =
          deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
      waitUntilMinimumProcessInstanceEventsExportedCount(4);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
      importAllZeebeEntitiesFromScratch();
      ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
      final String flowNodeId =
          getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
      zeebeExtension.addVariablesToScope(
          Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
      importAllZeebeEntitiesFromLastIndex();
      zeebeExtension.addVariablesToScope(
          processInstanceKey, Map.of("var1", "processInstanceScopeUpdatedValue"), true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

      // when (only process level gets updated)
      importAllZeebeEntitiesFromLastIndex();

      // then (only process level gets updated)
      savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
      assertThat(savedProcessInstance.getVariables())
          .extracting(
              SimpleProcessVariableDto::getName,
              SimpleProcessVariableDto::getValue,
              SimpleProcessVariableDto::getType)
          .containsExactlyInAnyOrder(
              Tuple.tuple(
                  "var1",
                  Collections.singletonList("processInstanceScopeUpdatedValue"),
                  STRING_TYPE),
              Tuple.tuple(
                  "var1", Collections.singletonList("flowNodeInstanceScopeValue"), STRING_TYPE));
    }

    // reset to a clean slate before the second scenario, mirroring @AfterEach/@BeforeEach
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // given (only flow node level gets updated)
    {
      final long processInstanceKey =
          deployProcessAndStartProcessInstanceWithVariables(
              Map.of("var1", "processInstanceScopeValue"));
      waitUntilMinimumProcessInstanceEventsExportedCount(1);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
      importAllZeebeEntitiesFromScratch();
      ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
      final String flowNodeId =
          getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
      zeebeExtension.addVariablesToScope(
          Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
      importAllZeebeEntitiesFromLastIndex();
      zeebeExtension.addVariablesToScope(
          Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeUpdatedValue"), true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

      // when (only flow node level gets updated)
      importAllZeebeEntitiesFromLastIndex();

      // then (only flow node level gets updated)
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
  }

  @Test
  public void zeebeVariableImport_updateTheTypeOfVariablesAndUpdateObjectVariable() {
    // Covers two independent variable-update scenarios: scenario 1 updating primitive variables
    // to different types; scenario 2 updating a nested object variable's fields. Each scenario is
    // reset to a clean Zeebe export index and clean Optimize data before it runs, mirroring what
    // @BeforeEach/@AfterEach would otherwise do between separate test methods.

    // given (update the type of variables)
    {
      final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(5);
      importAllZeebeEntitiesFromScratch();
      zeebeExtension.addVariablesToScope(
          processInstanceKey,
          Map.of("var1", false, "var2", "someValue", "var3", "", "var4", true, "var5", 123.0),
          true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(5);

      // when (update the type of variables)
      importAllZeebeEntitiesFromLastIndex();

      // then (update the type of variables)
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

    // reset to a clean slate before the second scenario, mirroring @AfterEach/@BeforeEach
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // given (update object variable)
    {
      final Map<String, Object> objectVar = new HashMap<>();
      objectVar.put("name", "Pond");
      objectVar.put("age", 28);
      objectVar.put("likes", List.of("optimize", "garlic"));
      final Map<String, Object> variables = new HashMap<>();
      variables.put("objectVar", objectVar);
      final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(variables);
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
      importAllZeebeEntitiesFromScratch();

      objectVar.put("age", 29);
      objectVar.put("likes", List.of("optimize", "garlic", "tofu"));
      zeebeExtension.addVariablesToScope(processInstanceKey, variables, true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

      // when (update object variable)
      importAllZeebeEntitiesFromLastIndex();

      // then (update object variable)
      final ProcessInstanceDto instance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
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
              Tuple.tuple(
                  "objectVar.likes._listSize", LONG.getId(), Collections.singletonList("3")));
    }
  }

  @Test
  public void
      zeebeVariableImport_updateFlowNodeLevelVariableWithPropagationOnlyUpdatesFlowNodeVariable() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
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
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
        Long.parseLong(flowNodeId), Map.of("var1", "updatedValue"), false);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

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
  public void zeebeVariableImport_updateVariableSeveralTimesInSameAndInSeveralBatches() {
    // Covers two independent variable-update batching scenarios: scenario 1 updates the variable
    // twice, importing both updates in one batch; scenario 2 updates it twice again on a fresh
    // instance, importing each update as its own batch (forced via maxImportPageSize=1). Scenario
    // 2 must run last since it mutates shared configuration, only reset by the next test method's
    // @BeforeEach.

    // given (same batch)
    {
      final long processInstanceKey =
          deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
      importAllZeebeEntitiesFromScratch();
      zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "firstUpdate"), true);
      zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "secondUpdate"), true);
      importAllZeebeEntitiesFromLastIndex();
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(2);

      // when (same batch)
      importAllZeebeEntitiesFromLastIndex();

      // then (same batch)
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

    // reset to a clean slate before the second scenario, mirroring @AfterEach/@BeforeEach
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // given (several batches)
    {
      embeddedOptimizeExtension
          .getConfigurationService()
          .getConfiguredZeebe()
          .setMaxImportPageSize(1);
      embeddedOptimizeExtension.reloadConfiguration();
      final long processInstanceKey =
          deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
      waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
      importAllZeebeEntitiesFromScratch();
      zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "firstUpdate"), true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);
      importAllZeebeEntitiesFromLastIndex();
      zeebeExtension.addVariablesToScope(processInstanceKey, Map.of("var1", "secondUpdate"), true);
      waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(2);

      // when (several batches)
      importAllZeebeEntitiesFromLastIndex();
      importAllZeebeEntitiesFromLastIndex();

      // then (several batches)
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

  private void waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(
      final int minExportedEventCount) {
    waitUntilMinimumVariableDocumentsWithIntentExportedCount(
        minExportedEventCount, VariableIntent.CREATED);
  }

  private void waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(
      final int minExportedEventCount) {
    waitUntilMinimumVariableDocumentsWithIntentExportedCount(
        minExportedEventCount, VariableIntent.UPDATED);
  }

  private void waitUntilMinimumVariableDocumentsWithIntentExportedCount(
      final int minExportedEventCount, final VariableIntent intent) {
    final TermsQueryContainer variableBoolQuery = new TermsQueryContainer();
    variableBoolQuery.addTermQuery(ZeebeVariableRecordDto.Fields.intent, intent.name());
    waitUntilMinimumDataExportedCount(
        minExportedEventCount, DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME, variableBoolQuery);
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
