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
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
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
  public void
      zeebeVariableImport_processStartedWithVariablesAndVariablesAddedAfterProcessStarted() {
    // Covers two independent scenarios exercising different code paths: scenario 1 variables
    // present at process start; scenario 2 variables added to scope after the process already
    // started. Each scenario is reset to a clean Zeebe export index and clean Optimize data
    // before it runs, mirroring what @BeforeEach/@AfterEach would otherwise do between separate
    // test methods.

    // given (started with variables)
    {
      final Long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables();

      // when (started with variables)
      waitUntilMinimumProcessInstanceEventsExportedCount(4);
      waitUntilMinimumVariableDocumentsExportedCount(1);
      importAllZeebeEntitiesFromScratch();

      // then (started with variables)
      final ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
      assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
    }

    // reset to a clean slate before the second scenario, mirroring @AfterEach/@BeforeEach
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // given (variables added after process started)
    {
      final ProcessInstanceEvent processInstanceEvent = deployProcessAndStartProcessInstance();
      waitUntilMinimumProcessInstanceEventsExportedCount(4);
      zeebeExtension.addVariablesToScope(
          processInstanceEvent.getProcessInstanceKey(), BASIC_VARIABLES, false);
      waitUntilMinimumVariableDocumentsExportedCount(5);

      // when (variables added after process started)
      importAllZeebeEntitiesFromScratch();

      // then (variables added after process started)
      final ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
      assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
    }
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
    waitUntilNumberOfDefinitionsExported(1);
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(2);
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
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(2);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then (object-shaped and list-shaped) — asserted exhaustively in one call since both
    // variables coexist on this single instance and no other variables are present
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
                Collections.singletonList("12345678901234567890")),
            Tuple.tuple("numericStringsList", STRING.getId(), numericStringList),
            // additional _listSize variable for lists
            Tuple.tuple(
                "numericStringsList._listSize", LONG.getId(), Collections.singletonList("11")));
  }

  @Test
  public void zeebeVariableImport_variablesWithSameNameOnDifferentScope() {
    // given
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), Map.of("var1", "someValue"));
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstanceKey));

    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", false), true);
    waitUntilMinimumVariableDocumentsExportedCount(2);

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
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
        getProcessInstanceForId(String.valueOf(startedInstance.getProcessInstanceKey()));

    final String flowNodeId =
        getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), processVariable, false);
    waitUntilMinimumVariableDocumentsExportedCount(1);

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
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    waitUntilMinimumVariableDocumentsExportedCount(10);

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
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    waitUntilMinimumVariableDocumentsExportedCount(10);

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
  public void
      zeebeVariableImport_unsupportedTypesGetIgnoredAndObjectVariablesExcludedInConfiguration() {
    // Covers two independent scenarios: scenario 1 that unsupported variable types (e.g. null)
    // are ignored on import; scenario 2 that object variables are excluded from import when
    // configured. Scenario 2 must run last since it mutates shared configuration, only reset by
    // the next test method's @BeforeEach. Each scenario is reset to a clean Zeebe export index
    // and clean Optimize data before it runs, mirroring what @BeforeEach/@AfterEach would
    // otherwise do between separate test methods.

    // given (unsupported types ignored)
    {
      final Map<String, Object> supportedAndUnsupportedVariables = new HashMap<>();
      supportedAndUnsupportedVariables.put("nullValue", null);
      supportedAndUnsupportedVariables.put("supportedVariable", "someValue");

      final Process deployedProcess =
          zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
      final long processInstanceKey =
          zeebeExtension.startProcessInstanceWithVariables(
              deployedProcess.getBpmnProcessId(), supportedAndUnsupportedVariables);
      waitUntilMinimumProcessInstanceEventsExportedCount(4);
      waitUntilMinimumVariableDocumentsExportedCount(2);

      // when (unsupported types ignored)
      importAllZeebeEntitiesFromScratch();

      // then (unsupported types ignored)
      final ProcessInstanceDto savedProcessInstance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));
      assertThat(savedProcessInstance.getVariables())
          .extracting(
              SimpleProcessVariableDto::getName,
              SimpleProcessVariableDto::getValue,
              SimpleProcessVariableDto::getType)
          .containsExactly(
              Tuple.tuple(
                  "supportedVariable", Collections.singletonList("someValue"), STRING_TYPE));
    }

    // reset to a clean slate before the second scenario, mirroring @AfterEach/@BeforeEach
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
    databaseIntegrationTestExtension.deleteAllOptimizeData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // given (object variables excluded in configuration)
    {
      embeddedOptimizeExtension
          .getConfigurationService()
          .getConfiguredZeebe()
          .setIncludeObjectVariableValue(false);
      final Map<String, Object> variables = Map.of("objectVar", PERSON_VARIABLES, "boolVar", true);

      final Process deployedProcess =
          zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
      final long processInstanceKey =
          zeebeExtension.startProcessInstanceWithVariables(
              deployedProcess.getBpmnProcessId(), variables);
      waitUntilMinimumProcessInstanceEventsExportedCount(4);
      waitUntilMinimumVariableDocumentsExportedCount(1);

      // when (object variables excluded in configuration)
      importAllZeebeEntitiesFromScratch();
      final ProcessInstanceDto instance =
          getProcessInstanceForId(String.valueOf(processInstanceKey));

      // then (object variables excluded in configuration)
      assertThat(instance.getVariables())
          .extracting(
              SimpleProcessVariableDto::getName,
              SimpleProcessVariableDto::getType,
              SimpleProcessVariableDto::getValue)
          .containsExactlyInAnyOrder(
              Tuple.tuple("boolVar", BOOLEAN.getId(), Collections.singletonList("true")));
    }
  }

  @Test
  public void zeebeVariableImport_importObjectAndListVariables() {
    // Covers two independent variable-shape checks on the same instance: an object-shaped
    // variable and a list-shaped variable, using distinct names so both coexist on one instance.

    // given
    final Map<String, Object> variables =
        Map.of("objectVar", PERSON_VARIABLES, "listVar", List.of("value1", "value2"));

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), variables);
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(2);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then (object-shaped and list-shaped) — asserted exhaustively in one call since both
    // variables coexist on this single instance and no other variables are present
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
            Tuple.tuple("objectVar.likes._listSize", LONG.getId(), Collections.singletonList("2")),
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
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.addVariablesToScope(startedInstanceKey, processVariables, false);
    waitUntilMinimumVariableDocumentsExportedCount(2);

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
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(2);
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

  private void waitUntilMinimumVariableDocumentsExportedCount(final int minExportedEventCount) {
    final TermsQueryContainer variableBoolQuery = new TermsQueryContainer();
    variableBoolQuery.addTermQuery(
        ZeebeVariableRecordDto.Fields.intent, VariableIntent.CREATED.name());

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
