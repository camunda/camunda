/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class ZeebeVariableUpdateImportIT extends AbstractZeebeIT {

  private final String PROCESS_ID = "demoProcess";
  private final Map<String, Object> VARIABLES = generateVariables();
  private final Map<String, Object> UPDATED_VARIABLES = generateUpdatedVariables();

  @Test
  public void zeebeVariableImport_importRecordsForTheCreationAndTheUpdateOfProcessVariablesOnSameBatch() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    zeebeExtension.addVariablesToScope(processInstanceKey, UPDATED_VARIABLES, true);
    waitUntilNumberOfDefinitionsExported(1);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(5);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void zeebeVariableImport_importRecordsForTheCreationAndTheUpdateOfProcessVariablesOnDifferentBatch() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(5);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey, UPDATED_VARIABLES, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(5);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void zeebeVariableImport_updateVariablesForInstancesInSeveralDefinitions() {
    // given
    final long processInstanceKey1 = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    zeebeExtension.addVariablesToScope(processInstanceKey1, UPDATED_VARIABLES, true);

    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long processInstanceKey2 =
      zeebeExtension.startProcessInstanceWithVariables(deployedProcess.getBpmnProcessId(), VARIABLES);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(10);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(processInstanceKey2, UPDATED_VARIABLES, true);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(10);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    ProcessInstanceDto savedProcessInstance1 =
      getProcessInstanceForId(String.valueOf(processInstanceKey1));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance1);
    ProcessInstanceDto savedProcessInstance2 =
      getProcessInstanceForId(String.valueOf(processInstanceKey2));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance2);
  }

  @Test
  public void zeebeVariableImport_updateVariableValueWithNullGetsIgnored() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    String flowNodeId = getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();
    Map<String, Object> newVariables = new HashMap<>();
    newVariables.put("var1", null);
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      newVariables,
      true
    );
    zeebeExtension.addVariablesToScope(
      Long.parseLong(flowNodeId),
      newVariables,
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(2);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE),
        Tuple.tuple("var1", Collections.singletonList("flowNodeInstanceScopeValue"), STRING_TYPE)
      );
  }

  @Test
  public void zeebeVariableImport_variableNameOnSeveralScopesOnlyProcessLevelGetsUpdated() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    String flowNodeId = getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      Map.of("var1", "processInstanceScopeUpdatedValue"),
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("processInstanceScopeUpdatedValue"), STRING_TYPE),
        Tuple.tuple("var1", Collections.singletonList("flowNodeInstanceScopeValue"), STRING_TYPE)
      );
  }

  @Test
  public void zeebeVariableImport_variableNameOnSeveralScopesOnlyFlowNodeLevelGetsUpdated() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(Map.of(
      "var1",
      "processInstanceScopeValue"
    ));
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    String flowNodeId = getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
      Long.parseLong(flowNodeId),
      Map.of("var1", "flowNodeInstanceScopeUpdatedValue"),
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("processInstanceScopeValue"), STRING_TYPE),
        Tuple.tuple("var1", Collections.singletonList("flowNodeInstanceScopeUpdatedValue"), STRING_TYPE)
      );
  }

  @Test
  public void zeebeVariableImport_updateTheTypeOfVariables() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(VARIABLES);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(5);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      Map.of("var1", false,
             "var2", "someValue",
             "var3", "",
             "var4", true,
             "var5", 123.0
      ),
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(5);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("false"), BOOLEAN_TYPE),
        Tuple.tuple("var2", Collections.singletonList("someValue"), STRING_TYPE),
        Tuple.tuple("var3", Collections.singletonList(""), STRING_TYPE),
        Tuple.tuple("var4", Collections.singletonList("true"), BOOLEAN_TYPE),
        Tuple.tuple("var5", Collections.singletonList("123.0"), DOUBLE_TYPE)
      );
  }

  @Test
  public void zeebeVariableImport_updateFlowNodeLevelVariableWithPropagationOnlyUpdatesFlowNodeVariable() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    String flowNodeId = getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(
      processInstanceEvent.getProcessInstanceKey(),
      Map.of("var1", "processInstanceScopeValue"),
      true
    );
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", "flowNodeInstanceScopeValue"), true);
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", "updatedValue"), false);
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("processInstanceScopeValue"), STRING_TYPE),
        Tuple.tuple("var1", Collections.singletonList("updatedValue"), STRING_TYPE)
      );
  }

  @Test
  public void zeebeVariableImport_updateVariableSeveralTimesInSameBatch() {
    // given
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      Map.of("var1", "firstUpdate"),
      true
    );
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      Map.of("var1", "secondUpdate"),
      true
    );
    importAllZeebeEntitiesFromLastIndex();
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(2);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("secondUpdate"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_updateVariableSeveralTimesInSeveralBatches() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    final long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables(Map.of("var1", "someValue"));
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      Map.of("var1", "firstUpdate"),
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);
    importAllZeebeEntitiesFromLastIndex();
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      Map.of("var1", "secondUpdate"),
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(2);

    // when
    importAllZeebeEntitiesFromLastIndex();
    importAllZeebeEntitiesFromLastIndex();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("secondUpdate"), STRING_TYPE));
  }

  @SneakyThrows
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
    waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    objectVar.put("age", 29);
    objectVar.put("likes", List.of("optimize", "garlic", "tofu"));
    zeebeExtension.addVariablesToScope(
      processInstanceKey,
      variables,
      true
    );
    waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(1);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(instance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          "objectVar",
          OBJECT.getId(),
          Collections.singletonList(variablesClient.createMapJsonObjectVariableDto(objectVar).getValue())
        ),
        Tuple.tuple("objectVar.name", STRING.getId(), Collections.singletonList("Pond")),
        Tuple.tuple("objectVar.age", DOUBLE.getId(), Collections.singletonList("29.0")),
        Tuple.tuple("objectVar.likes", STRING.getId(), List.of("optimize", "garlic", "tofu")),
        Tuple.tuple("objectVar.likes._listSize", LONG.getId(), Collections.singletonList("3"))
      );
  }

  private Map<String, Object> generateVariables() {
    return Map.of("var1", "someValue",
                  "var2", false,
                  "var3", 123,
                  "var4", 123.3,
                  "var5", ""
    );
  }

  private Map<String, Object> generateUpdatedVariables() {
    return Map.of("var1", "var1UpdatedValue",
                  "var2", true,
                  "var3", 123.0,
                  "var4", 123,
                  "var5", "var5UpdatedValue"
    );
  }

  private ProcessInstanceDto getProcessInstanceForId(final String processInstanceId) {
    return elasticSearchIntegrationTestExtension.getAllProcessInstances()
      .stream()
      .filter(instance -> instance.getProcessInstanceId().equals(processInstanceId))
      .collect(Collectors.toList()).stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException("No process instance with id " + processInstanceId +
                                                                "found"));
  }

  private void waitUntilMinimumVariableDocumentsWithCreatedIntentExportedCount(final int minExportedEventCount) {
    waitUntilMinimumVariableDocumentsWithIntentExportedCount(minExportedEventCount, VariableIntent.CREATED);
  }

  private void waitUntilMinimumVariableDocumentsWithUpdatedIntentExportedCount(final int minExportedEventCount) {
    waitUntilMinimumVariableDocumentsWithIntentExportedCount(minExportedEventCount, VariableIntent.UPDATED);
  }

  private void waitUntilMinimumVariableDocumentsWithIntentExportedCount(final int minExportedEventCount,
                                                                        final VariableIntent intent) {
    BoolQueryBuilder variableBoolQueryBuilder = boolQuery().must(termsQuery(
      ZeebeVariableRecordDto.Fields.intent,
      intent.name()
    ));
    waitUntilMinimumDataExportedCount(
      minExportedEventCount,
      ElasticsearchConstants.ZEEBE_VARIABLE_INDEX_NAME,
      variableBoolQueryBuilder
    );
  }

  private void assertThatVariablesHaveBeenImportedForProcessInstance(final ProcessInstanceDto processInstanceDto) {
    assertThat(processInstanceDto.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("var1UpdatedValue"), STRING_TYPE),
        Tuple.tuple("var2", Collections.singletonList("true"), BOOLEAN_TYPE),
        Tuple.tuple("var3", Collections.singletonList("123.0"), DOUBLE_TYPE),
        Tuple.tuple("var4", Collections.singletonList("123"), DOUBLE_TYPE),
        Tuple.tuple("var5", Collections.singletonList("var5UpdatedValue"), STRING_TYPE)
      );
  }

  private long deployProcessAndStartProcessInstanceWithVariables(Map<String, Object> variablesToAdd) {
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceWithVariables(deployedProcess.getBpmnProcessId(), variablesToAdd);
  }

  private ProcessInstanceEvent deployProcessAndStartProcessInstance() {
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

}
