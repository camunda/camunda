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
  public void variableImportWorksForLongStringsAndDateStrings() {
    // Covers two independent variable-content edge cases on the same instance: a too-long string
    // value, and a date-formatted string value.

    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    // see https://www.elastic.co/guide/en/elasticsearch/reference/7.15/ignore-above.html
    final String longStringVariableName = "longStringVar";
    // use a too long value of a length > 32766
    final String largeValue = RandomStringUtils.randomAlphabetic(32767);
    final String dateVariableName = "date";
    final String dateVariableValue = "2025-10-10";
    final String parsedDateValue = "2025-10-10T00:00:00.000+0000";
    final Map<String, Object> variables =
        Map.of(longStringVariableName, largeValue, dateVariableName, dateVariableValue);
    final Long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);

    // when
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, processInstanceKey);
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto importedProcessInstance =
        getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then (long string)
    assertThat(importedProcessInstance.getVariables())
        .filteredOn(variable -> variable.getName().equals(longStringVariableName))
        .singleElement()
        .satisfies(
            variable -> {
              assertThat(variable.getName()).isEqualTo(longStringVariableName);
              assertThat(variable.getValue().get(0)).isEqualTo(largeValue);
            });

    // then (date string)
    assertThat(importedProcessInstance.getVariables())
        .filteredOn(variable -> variable.getName().equals(dateVariableName))
        .singleElement()
        .satisfies(
            variable -> {
              assertThat(variable.getName()).isEqualTo(dateVariableName);
              assertThat(variable.getType()).isEqualTo(DATE.getId());
              assertThat(variable.getValue().getFirst()).isEqualTo(parsedDateValue);
            });
  }

  @Test
  public void variableObjectAndListImportDoNotParseDigitOnlyStringsAsDates() {
    // Covers two independent variable-shape edge cases on the same instance: an object-shaped and
    // a list-shaped variable, both containing digit-only strings that must not be parsed as dates.

    // given
    final Map<String, Object> numericStringMap =
        generateNumericStringsOfLengthXInRange(10, 20).stream()
            .collect(Collectors.toMap(str -> String.valueOf(str.length()), str -> str));
    final var numericStringList = generateNumericStringsOfLengthXInRange(10, 20);
    final Map<String, Object> variables =
        Map.of("numericStrings", numericStringMap, "numericStringsList", numericStringList);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKey);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(2, processInstanceKey);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then (object-shaped)
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .contains(
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

    // then (list-shaped)
    assertThat(instance.getVariables())
        .extracting(
            SimpleProcessVariableDto::getName,
            SimpleProcessVariableDto::getType,
            SimpleProcessVariableDto::getValue)
        .contains(
            Tuple.tuple("numericStringsList", STRING.getId(), numericStringList),
            // additional _listSize variable for lists
            Tuple.tuple(
                "numericStringsList._listSize", LONG.getId(), Collections.singletonList("11")));
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
  public void
      zeebeVariableImport_importObjectVariablesAndWhenObjectVariablesAreExcludedInConfiguration() {
    // Covers two independent object-variable-import scenarios: scenario 1 imports the full nested
    // object value under the default configuration; scenario 2 imports with object-variable-value
    // inclusion turned off, so only the plain (non-object) variable survives. Scenario 2 must run
    // last since it mutates shared configuration, only reset by the next test method's @BeforeEach.

    // given (default config)
    final Map<String, Object> variablesA = Map.of("objectVar", PERSON_VARIABLES);

    final Process deployedProcessA =
        zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKeyA =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcessA.getBpmnProcessId(), variablesA);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKeyA);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKeyA);

    // when (default config)
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instanceA =
        getProcessInstanceForId(String.valueOf(processInstanceKeyA));

    // then (default config)
    assertThat(instanceA.getVariables())
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

    // given (object variables excluded in configuration)
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setIncludeObjectVariableValue(false);
    final Map<String, Object> variablesB = Map.of("objectVar", PERSON_VARIABLES, "boolVar", true);

    final Process deployedProcessB =
        zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKeyB =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcessB.getBpmnProcessId(), variablesB);
    waitUntilMinimumProcessInstanceEventsForInstanceExportedCount(4, processInstanceKeyB);
    waitUntilMinimumVariableDocumentsForInstanceExportedCount(1, processInstanceKeyB);

    // when (object variables excluded in configuration)
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instanceB =
        getProcessInstanceForId(String.valueOf(processInstanceKeyB));

    // then (object variables excluded in configuration)
    assertThat(instanceB.getVariables())
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
