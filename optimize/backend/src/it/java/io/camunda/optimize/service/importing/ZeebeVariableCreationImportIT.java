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
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZeebeVariableCreationImportIT extends AbstractCCSMIT {

  private static final String PROCESS_ID = "demoProcess";
  private static final Map<String, Object> BASIC_VARIABLES =
      Map.of("var1", "someValue", "var2", false, "var3", 123, "var4", 123.3, "var5", "");
  private static final Map<String, Object> PERSON_VARIABLES =
      Map.of(
          "name",
          "Pond",
          "age",
          28,
          "IQ",
          99999999999999L,
          "birthday",
          "1992-11-17T00:00:00+01:00",
          "muscleMassInPercent",
          99.9,
          "deceased",
          false,
          "hands",
          (short) 2,
          "likes",
          List.of("optimize", "garlic"),
          "skills",
          Map.of("read", true, "write", false));

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setIncludeObjectVariableValue(true);
  }

  @Test
  public void zeebeVariableImport_processStartedWithVariables() {
    // given
    final Long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables();

    // when
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(5, processInstanceKey);
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void variableImportWorksForLongStrings() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    // see https://www.elastic.co/guide/en/elasticsearch/reference/7.15/ignore-above.html
    final String variableName = "longStringVar";
    // use a too long value of a length > 32766
    final String largeValue = RandomStringUtils.randomAlphabetic(32767);
    final Map<String, Object> variables = Map.of(variableName, largeValue);
    final Long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);

    // when
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessInstanceDto importedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(importedProcessInstance.getVariables())
        .singleElement()
        .satisfies(
            variable -> {
              assertThat(variable.getName()).isEqualTo(variableName);
              assertThat(variable.getValue().get(0)).isEqualTo(largeValue);
            });
  }

  @Test
  public void variableImportWorksForDateStrings() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final String variableName = "date";
    final String variableValue = "2025-10-10";
    final String parsedDateValue = "2025-10-10T00:00:00.000+0000";
    final Map<String, Object> variables = Map.of(variableName, variableValue);
    final Long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);

    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessInstanceDto importedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(importedProcessInstance.getVariables())
        .singleElement()
        .satisfies(
            variable -> {
              assertThat(variable.getName()).isEqualTo(variableName);
              assertThat(variable.getType()).isEqualTo(DATE.getId());
              assertThat(variable.getValue().getFirst()).isEqualTo(parsedDateValue);
            });
  }

  @Test
  public void variableObjectImportDoesNotParseDigitOnlyStringsAsDates() {
    // given
    final Map<String, Object> numericStringMap =
        generateNumericStringsOfLengthXInRange(10, 20).stream()
            .collect(Collectors.toMap(str -> String.valueOf(str.length()), str -> str));

    final Map<String, Object> variables = Map.of("numericStrings", numericStringMap);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "numericStrings",
                OBJECT.getId(),
                Collections.singletonList(
                    variablesClient.createMapJsonObjectVariableDto(numericStringMap).getValue())),
            Tuple.tuple(
                "numericStrings.10", STRING.getId(), Collections.singletonList("1234567890")),
            Tuple.tuple(
                "numericStrings.11", STRING.getId(), Collections.singletonList("12345678901")),
            Tuple.tuple(
                "numericStrings.12", STRING.getId(), Collections.singletonList("123456789012")),
            Tuple.tuple(
                "numericStrings.13", STRING.getId(), Collections.singletonList("1234567890123")),
            Tuple.tuple(
                "numericStrings.14", STRING.getId(), Collections.singletonList("12345678901234")),
            Tuple.tuple(
                "numericStrings.15", STRING.getId(), Collections.singletonList("123456789012345")),
            Tuple.tuple(
                "numericStrings.16", STRING.getId(), Collections.singletonList("1234567890123456")),
            Tuple.tuple(
                "numericStrings.17",
                STRING.getId(),
                Collections.singletonList("12345678901234567")),
            Tuple.tuple(
                "numericStrings.18",
                STRING.getId(),
                Collections.singletonList("123456789012345678")),
            Tuple.tuple(
                "numericStrings.19",
                STRING.getId(),
                Collections.singletonList("1234567890123456789")),
            Tuple.tuple(
                "numericStrings.20",
                STRING.getId(),
                Collections.singletonList("12345678901234567890")));
  }

  @Test
  public void variableListImportDoesNotParseDigitOnlyStringsAsDates() {
    // given
    final var numericStringList = generateNumericStringsOfLengthXInRange(10, 20);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), Map.of("numericStrings", numericStringList));

    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple("numericStrings", STRING.getId(), numericStringList),
            // additional _listSize variable for lists
            Tuple.tuple("numericStrings._listSize", LONG.getId(), Collections.singletonList("11")));
  }

  @Test
  public void zeebeVariableImport_variablesAddedAfterProcessStarted() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(
        4, processInstanceEvent.getProcessInstanceKey());
    zeebeExtension.addVariablesToScope(
        processInstanceEvent.getProcessInstanceKey(), BASIC_VARIABLES, false);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(
        5, processInstanceEvent.getProcessInstanceKey());

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void zeebeVariableImport_variablesWithSameNameOnDifferentScope() {
    // given
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), Map.of("var1", "someValue"));
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, startedInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, startedInstanceKey);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstanceKey));

    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", false), true);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, startedInstanceKey);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE),
            Tuple.tuple("var1", Collections.singletonList("false"), BOOLEAN_TYPE));
  }

  @Test
  public void zeebeVariableImport_addNonLocalVariableToFlowNodeInstance() {
    // given
    final Map<String, Object> processVariable = Map.of("var1", "someValue");
    final ProcessInstanceEvent startedInstance = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(
        4, startedInstance.getProcessInstanceKey());
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstance.getProcessInstanceKey()));

    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), processVariable, false);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(
        1, startedInstance.getProcessInstanceKey());

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstance.getProcessInstanceKey()));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactly(Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_variablesForMultipleInstancesStartedForSameProcess() {
    // given
    final long deployedInstanceKey1 = deployProcessAndStartProcessInstanceWithVariables();
    final long deployedInstanceKey2 = deployProcessAndStartProcessInstanceWithVariables();
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, deployedInstanceKey1);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, deployedInstanceKey2);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(5, deployedInstanceKey1);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(5, deployedInstanceKey2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance1 =
        getProcessInstanceForId(String.valueOf(deployedInstanceKey1));
    final ProcessInstanceDto savedProcessInstance2 =
        getProcessInstanceForId(String.valueOf(deployedInstanceKey2));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance1);
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance2);
  }

  @Test
  public void zeebeVariableImport_variablesForMultipleInstancesStartedForDifferentProcesses() {
    // given
    final Process deployedProcess1 =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey1 = deployProcessAndStartProcessInstanceWithVariables();
    final Process deployedProcess2 =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess("second_process"));
    final long startedInstanceKey2 =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess2.getBpmnProcessId(), BASIC_VARIABLES);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, startedInstanceKey1);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, startedInstanceKey2);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(5, startedInstanceKey1);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(5, startedInstanceKey2);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final ProcessInstanceDto savedProcessInstance1 =
        getProcessInstanceForId(String.valueOf(startedInstanceKey1));
    final ProcessInstanceDto savedProcessInstance2 =
        getProcessInstanceForId(String.valueOf(startedInstanceKey2));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance1);
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance2);
  }

  @Test
  public void zeebeVariableImport_unsupportedTypesGetIgnored() {
    // given
    final Map<String, Object> supportedAndUnsupportedVariables = new HashMap<>();
    supportedAndUnsupportedVariables.put("nullValue", null);
    supportedAndUnsupportedVariables.put("supportedVariable", "someValue");

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), supportedAndUnsupportedVariables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactly(
            Tuple.tuple("supportedVariable", Collections.singletonList("someValue"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_importObjectVariables() {
    // given
    final Map<String, Object> variables = Map.of("objectVar", PERSON_VARIABLES);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
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
                    variablesClient.createMapJsonObjectVariableDto(PERSON_VARIABLES).getValue())),
            Tuple.tuple("objectVar.name", STRING.getId(), Collections.singletonList("Pond")),
            Tuple.tuple("objectVar.age", DOUBLE.getId(), Collections.singletonList("28.0")),
            Tuple.tuple(
                "objectVar.IQ", DOUBLE.getId(), Collections.singletonList("9.9999999999999E13")),
            Tuple.tuple(
                "objectVar.birthday",
                DATE.getId(),
                Collections.singletonList("1992-11-17T00:00:00.000+0100")),
            Tuple.tuple(
                "objectVar.muscleMassInPercent", DOUBLE.getId(), Collections.singletonList("99.9")),
            Tuple.tuple("objectVar.deceased", BOOLEAN.getId(), Collections.singletonList("false")),
            Tuple.tuple("objectVar.hands", DOUBLE.getId(), Collections.singletonList("2.0")),
            Tuple.tuple(
                "objectVar.skills.read", BOOLEAN.getId(), Collections.singletonList("true")),
            Tuple.tuple(
                "objectVar.skills.write", BOOLEAN.getId(), Collections.singletonList("false")),
            Tuple.tuple("objectVar.likes", STRING.getId(), List.of("optimize", "garlic")),
            // additional _listSize variable for lists
            Tuple.tuple("objectVar.likes._listSize", LONG.getId(), Collections.singletonList("2")));
  }

  @Test
  public void
      zeebeVariableImport_importObjectVariablesWhenObjectVariablesAreExcludedInConfiguration() {
    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setIncludeObjectVariableValue(false);
    final Map<String, Object> variables = Map.of("objectVar", PERSON_VARIABLES, "boolVar", true);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple("boolVar", BOOLEAN.getId(), Collections.singletonList("true")));
  }

  @Test
  public void zeebeVariableImport_importListVariables() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), Map.of("listVar", List.of("value1", "value2")));
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple("listVar", STRING.getId(), List.of("value1", "value2")),
            // additional _listSize variable for lists
            Tuple.tuple("listVar._listSize", LONG.getId(), Collections.singletonList("2")));
  }

  @Test
  public void zeebeVariableImport_flattensDeeplyNestedObjectVariableAtEachLevel() {
    // Regression for epic camunda/product-hub#3785 (case 6): with flattening enabled (see the
    // @BeforeEach setup), a multi-level nested object must flatten correctly at each level with the
    // correct types preserved, not just one level deep.
    // given
    final Map<String, Object> geo = new HashMap<>();
    geo.put("lat", 52.52);
    geo.put("lon", 13.405);
    final Map<String, Object> address = new HashMap<>();
    address.put("city", "Berlin");
    address.put("geo", geo);
    final Map<String, Object> user = new HashMap<>();
    user.put("firstName", "Ada");
    user.put("lastName", "Lovelace");
    user.put("address", address);
    final Map<String, Object> variables = Map.of("user", user);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then the object is flattened at every level (String at each string level, Double for the
    // numeric coordinates) and the raw value is stored
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "user",
                OBJECT.getId(),
                Collections.singletonList(
                    variablesClient.createMapJsonObjectVariableDto(user).getValue())),
            Tuple.tuple("user.firstName", STRING.getId(), Collections.singletonList("Ada")),
            Tuple.tuple("user.lastName", STRING.getId(), Collections.singletonList("Lovelace")),
            Tuple.tuple("user.address.city", STRING.getId(), Collections.singletonList("Berlin")),
            Tuple.tuple("user.address.geo.lat", DOUBLE.getId(), Collections.singletonList("52.52")),
            Tuple.tuple(
                "user.address.geo.lon", DOUBLE.getId(), Collections.singletonList("13.405")));
  }

  @Test
  public void zeebeVariableImport_handlesTopLevelAndNestedArrayVariablesConsistently() {
    // Regression for epic camunda/product-hub#3785 (case 7): both a plain top-level array variable
    // and an array nested inside an object variable must be handled consistently (each as a
    // multi-value variable with a _listSize indicator), with no crash either way. A top-level
    // primitive array stores no raw object value, whereas the enclosing object variable does store
    // its raw value. Flattening is enabled via the @BeforeEach setup.
    // given
    final Map<String, Object> objectWithArray = new HashMap<>();
    objectWithArray.put("tags", List.of("a", "b", "c"));
    final Map<String, Object> variables = new HashMap<>();
    variables.put("topLevelArray", List.of("x", "y"));
    variables.put("objectWithArray", objectWithArray);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple("topLevelArray", STRING.getId(), List.of("x", "y")),
            Tuple.tuple("topLevelArray._listSize", LONG.getId(), Collections.singletonList("2")),
            Tuple.tuple(
                "objectWithArray",
                OBJECT.getId(),
                Collections.singletonList(
                    variablesClient.createMapJsonObjectVariableDto(objectWithArray).getValue())),
            Tuple.tuple("objectWithArray.tags", STRING.getId(), List.of("a", "b", "c")),
            Tuple.tuple(
                "objectWithArray.tags._listSize", LONG.getId(), Collections.singletonList("3")));
  }

  @Test
  public void zeebeVariableImport_importsEmptyObjectAndOmitsNullValuedVariable() {
    // Characterization for epic camunda/product-hub#3785 (case 8), flagged during manual QA as
    // worth a second look. Flattening is enabled via the @BeforeEach setup.
    //  - An empty object ({}) imports without error and is stored as an OBJECT variable with no
    //    flattened sub-fields.
    //  - A null-valued variable does NOT cause an error and is OMITTED entirely (it is neither
    //    flattened nor stored as null). This matches the behavior already verified by
    //    zeebeVariableImport_unsupportedTypesGetIgnored. Asserted here as the current behavior so a
    //    reviewer can decide whether null-valued variables should instead be stored explicitly.
    // given
    final Map<String, Object> emptyObject = Map.of();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("emptyObjectVar", emptyObject);
    variables.put("nullVar", null);
    variables.put("controlVar", "keep");

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(3, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then the empty object is stored as a raw OBJECT value with no flattened sub-fields, and the
    // null-valued variable is omitted
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "emptyObjectVar",
                OBJECT.getId(),
                Collections.singletonList(
                    variablesClient.createMapJsonObjectVariableDto(emptyObject).getValue())),
            Tuple.tuple("controlVar", STRING_TYPE, Collections.singletonList("keep")));
  }

  @Test
  public void zeebeVariableImport_importVariablesInBatches() {
    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    final Map<String, Object> processVariables = Map.of("var1", "someValue1", "var2", "someValue2");
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), processVariables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, startedInstanceKey);
    zeebeExtension.addVariablesToScope(startedInstanceKey, processVariables, false);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, startedInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstanceKey));
    importAllZeebeEntitiesFromLastIndex();

    // then
    assertThat(savedProcessInstance.getVariables()).hasSize(1);
    savedProcessInstance = getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .hasSize(2)
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("someValue1"), STRING_TYPE),
            Tuple.tuple("var2", Collections.singletonList("someValue2"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_importZeebeVariableDataFromMultipleDays() {
    // given
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), Map.of("var1", "someValue1"));

    try {
      zeebeExtension.setClock(Instant.now().plus(1, ChronoUnit.DAYS));
    } catch (final IOException | InterruptedException e) {
      throw new OptimizeRuntimeException(e);
    }
    zeebeExtension.addVariablesToScope(startedInstanceKey, Map.of("var2", "someValue2"), false);

    // when
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, startedInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, startedInstanceKey);
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(savedProcessInstance.getVariables())
        .hasSize(2)
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("someValue1"), STRING_TYPE),
            Tuple.tuple("var2", Collections.singletonList("someValue2"), STRING_TYPE));
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

  private void assertThatVariablesHaveBeenImportedForProcessInstance(
      final ProcessInstanceDto processInstanceDto) {
    assertThat(processInstanceDto.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getValue,
            SimpleProcessVariableDto::getType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE),
            Tuple.tuple("var2", Collections.singletonList("false"), BOOLEAN_TYPE),
            Tuple.tuple("var3", Collections.singletonList("123"), DOUBLE_TYPE),
            Tuple.tuple("var4", Collections.singletonList("123.3"), DOUBLE_TYPE),
            Tuple.tuple("var5", Collections.singletonList(""), STRING_TYPE));
  }

  private ProcessInstanceEvent deployProcessAndStartProcessInstance() {
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

  private Long deployProcessAndStartProcessInstanceWithVariables() {
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceWithVariables(
        deployedProcess.getBpmnProcessId(), BASIC_VARIABLES);
  }

  private List<String> generateNumericStringsOfLengthXInRange(
      final int startingStringLength, final int endInclusiveStringLength) {
    final var digits = "1234567890";
    return IntStream.rangeClosed(startingStringLength, endInclusiveStringLength)
        .mapToObj(len -> digits.repeat((len / 10) + 1).substring(0, len))
        .toList();
  }
}
