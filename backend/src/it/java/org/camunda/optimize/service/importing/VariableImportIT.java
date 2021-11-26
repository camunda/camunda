/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.nio.CharBuffer;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class VariableImportIT extends AbstractImportIT {

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortVariableInstanceImport() {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // then
    assertThatEveryFlowNodeWasExecuted4Times(firstProcInst.getProcessDefinitionKey());
  }

  @Test
  public void variableImportWorks() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariableNamesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
  }

  @Test
  public void variableImportUsesDefaultTenant() throws JsonProcessingException {
    // given
    final String defaultTenant = "crab";
    embeddedOptimizeExtension.getDefaultEngineConfiguration()
      .setDefaultTenant(new DefaultTenant(defaultTenant, defaultTenant));
    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(getSingleServiceTaskProcess(), variables);
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getStoredVariableUpdateInstances())
      .hasSize(variables.size())
      .allSatisfy(variableUpdate -> assertThat(variableUpdate.getTenantId()).isEqualTo(defaultTenant));
  }

  @Test
  public void variableImportWorks_evenIfSeriesOfEsUpdateFailures() throws JsonProcessingException {
    // given
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getStoredVariableUpdateInstances()).isEmpty();

    // when
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto = engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables
    );

    // whenES update writes fail
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest variableImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "\""));
    esMockServer
      .when(variableImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importAllEngineEntitiesFromLastIndex();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariableNamesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then variables are stored as expected after ES writes successful
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
    esMockServer.verify(variableImportMatcher);
  }

  @Test
  public void variableImportExcludesVariableInstanceWritingIfFeatureDisabled() throws JsonProcessingException {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);

    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariableNamesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).isEmpty();
  }

  @SneakyThrows
  @Test
  public void objectVariablesAreImportedAndFlattened() {
    // given
    final Map<String, Object> person = createPersonVariableWithAllTypes();
    final VariableDto objectVariableDto = variablesClient.createMapJsonObjectVariableDto(person);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);
    final List<Tuple> expectedVariables = List.of(
      Tuple.tuple("objectVar", OBJECT.getId(), objectVariableDto.getValue()),
      Tuple.tuple("objectVar.name", STRING.getId(), "Pond"),
      Tuple.tuple("objectVar.age", DOUBLE.getId(), "28.0"),
      Tuple.tuple("objectVar.IQ", DOUBLE.getId(), "9.9999999999999E13"),
      Tuple.tuple("objectVar.birthday", DATE.getId(), "1992-11-17T00:00:00.000+0100"),
      Tuple.tuple("objectVar.muscleMassInPercent", DOUBLE.getId(), "99.9"),
      Tuple.tuple("objectVar.deceased", BOOLEAN.getId(), "false"),
      Tuple.tuple("objectVar.hands", DOUBLE.getId(), "2.0"),
      Tuple.tuple("objectVar.skills.read", BOOLEAN.getId(), "true"),
      Tuple.tuple("objectVar.skills.write", BOOLEAN.getId(), "false"),
      Tuple.tuple("objectVar.likes._listSize", LONG.getId(), "2") // additional _listSize variable for lists
    );

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);
    final List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(instanceVariables)
      .hasSize(11)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrderElementsOf(expectedVariables);
    assertThat(storedVariableUpdateInstances)
      .hasSize(11)
      .allSatisfy(var -> {
        assertThat(var.getProcessInstanceId()).isEqualTo(instanceDto.getId());
        assertThat(var.getTenantId()).isNull();
        assertThat(var.getInstanceId()).isNotNull();
      });
    assertThat(storedVariableUpdateInstances.stream().map(VariableUpdateInstanceDto::getTimestamp).collect(toSet()))
      .singleElement() // all flattened variables have the same timestamp as the original object variable
      .isNotNull();
    assertThat(storedVariableUpdateInstances)
      .extracting(
        VariableUpdateInstanceDto::getName,
        VariableUpdateInstanceDto::getType,
        VariableUpdateInstanceDto::getValue
      )
      .containsExactlyInAnyOrderElementsOf(expectedVariables);
  }

  @SneakyThrows
  @Test
  public void objectVariableJsonIsFormatted() {
    // given
    final Map<String, Object> objectVar = new HashMap<>();
    objectVar.put("name", "Pond");
    objectVar.put("age", "28");
    objectVar.put("likes", List.of("optimize", "garlic"));
    final VariableDto objectVariableDto = variablesClient.createJsonObjectVariableDto(
      new ObjectMapper().writeValueAsString(objectVar), // map variable without indents/newline formatting
      "java.util.HashMap"
    );
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then
    assertThat(instanceVariables)
      .filteredOn(variable -> "objectVar".equals(variable.getName()))
      .singleElement()
      .extracting(
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactly(
        OBJECT.getId(), new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(objectVar)
      );
  }

  @SneakyThrows
  @Test
  public void flattenedObjectVariablesAreUpdated() {
    // given an instance with an object variable
    final Map<String, Object> objectVar = new HashMap<>();
    objectVar.put("name", "Pond");
    objectVar.put("age", 28);
    objectVar.put("likes", List.of("optimize", "garlic"));
    VariableDto objectVariableDto = variablesClient.createMapJsonObjectVariableDto(objectVar);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(getSingleUserTaskDiagram(), variables);
    importAllEngineEntitiesFromScratch();

    // and the variable is updated while the instance is running
    objectVar.put("age", 29);
    objectVar.put("likes", List.of("optimize", "garlic", "tofu"));
    objectVariableDto = variablesClient.createMapJsonObjectVariableDto(objectVar);
    engineIntegrationExtension.updateVariableInstanceForProcessInstance(
      instance.getId(),
      "objectVar",
      new ObjectMapper().writeValueAsString(objectVariableDto)
    );
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    importAllEngineEntitiesFromLastIndex();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instance);

    // then
    assertThat(instanceVariables)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getVersion
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectVar", OBJECT.getId(), objectVariableDto.getValue(), 2L),
        Tuple.tuple("objectVar.name", STRING.getId(), "Pond", 2L),
        Tuple.tuple("objectVar.age", DOUBLE.getId(), "29.0", 2L),
        Tuple.tuple("objectVar.likes._listSize", LONG.getId(), "3", 2L)
      );
  }

  @SneakyThrows
  @Test
  public void listVariablesAreFlattened() {
    // given
    final Map<String, Object> pet1 = new HashMap<>();
    final Map<String, Object> pet2 = new HashMap<>();
    pet1.put("name", "aDog");
    pet1.put("type", "dog");
    pet2.put("name", "aCat");
    pet2.put("type", "cat");
    final VariableDto objectVariableDto = createListJsonObjectVariableDto(List.of(pet1, pet2));
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectListVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then
    assertThat(instanceVariables)
      .hasSize(2)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectListVar", OBJECT.getId(), objectVariableDto.getValue()),
        Tuple.tuple("objectListVar._listSize", LONG.getId(), "2")
      );
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("primitiveObjectVariableScenarios")
  public void objectVariablesThatArePrimitivesAreNotDuplicated(final String value, final String objectTypeName,
                                                               final VariableType expectedType,
                                                               final String expectedValue) {
    // given
    final VariableDto objectVariableDto =
      variablesClient.createJsonObjectVariableDto(value, objectTypeName);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then we only get one primitive variable as the result and no object variable as the var is not really an "object"
    assertThat(instanceVariables)
      .singleElement()
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactly("objectVar", expectedType.getId(), expectedValue);
  }

  @SneakyThrows
  @Test
  public void differentDateFormatForObjectVariableDateProperties() {
    // given
    final Map<String, Object> objectVar = new HashMap<>();
    objectVar.put(
      "dateProperty",
      new Date(OffsetDateTime.parse("2021-11-01T05:05:00+01:00").toInstant().toEpochMilli())
    );
    final SimpleDateFormat objectVarDateFormat = new SimpleDateFormat("EEEEE dd MMMMM yyyy HH:mm:ss.SSSZ");
    final VariableDto objectVariableDto = variablesClient.createJsonObjectVariableDto(
      new ObjectMapper().setDateFormat(objectVarDateFormat)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValueAsString(objectVar),
      "java.util.HashMap"
    );
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then
    assertThat(instanceVariables)
      .hasSize(2)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectVar", OBJECT.getId(), objectVariableDto.getValue()),
        Tuple.tuple("objectVar.dateProperty", DATE.getId(), "2021-11-01T05:05:00.000+0100")
      );
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void objectVariableValueIsFetchedDependingOnConfig(final boolean includeObjectVariableValue) {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .setEngineImportVariableIncludeObjectVariableValue(includeObjectVariableValue);

    final VariableDto objectVariableDto = variablesClient.createMapJsonObjectVariableDto(null);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var", objectVariableDto);
    deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    final ClientAndServer engineMockServer = useAndGetEngineMockServer();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    engineMockServer.verify(
      request()
        .withPath(engineIntegrationExtension.getEnginePath() + EngineConstants.VARIABLE_UPDATE_ENDPOINT)
        .withQueryStringParameter("occurredAfter", ".*")
        // our config has include semantics, api has exclude semantics, thus opposite is expected
        .withQueryStringParameter("excludeObjectValues", String.valueOf(!includeObjectVariableValue)),
      VerificationTimes.once()
    );
    engineMockServer.verify(
      request()
        .withPath(engineIntegrationExtension.getEnginePath() + EngineConstants.VARIABLE_UPDATE_ENDPOINT)
        .withQueryStringParameter("occurredAt", ".*")
        // our config has include semantics, api has exclude semantics, thus opposite is expected
        .withQueryStringParameter("excludeObjectValues", String.valueOf(!includeObjectVariableValue)),
      VerificationTimes.once()
    );
  }

  @Test
  public void variableUpdateImport() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleUserTaskDiagram();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariableNamesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
  }

  @Test
  public void primitiveVariablesCanHaveNullValue() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveVariableTypesWithNullValues();
    engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    SearchResponse responseForAllDocumentsOfIndex = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    for (SearchHit searchHit : responseForAllDocumentsOfIndex.getHits()) {
      @SuppressWarnings(UNCHECKED_CAST)
      List<Map> retrievedVariables = (List<Map>) searchHit.getSourceAsMap().get(VARIABLES);
      assertThat(retrievedVariables).hasSize(variables.size());
      retrievedVariables.forEach(var -> assertThat(var.get(VARIABLE_VALUE)).isNull());
    }
    assertThat(storedVariableUpdateInstances)
      .hasSize(variables.size())
      .allSatisfy(instance -> assertThat(instance.getValue()).isNull());
  }

  @SneakyThrows
  @Test
  public void objectVariablesCanHaveNullValue() {
    // given
    VariableDto objectVariableDto = variablesClient.createJsonObjectVariableDto(null, "java.util.HashMap");
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(getSingleUserTaskDiagram(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instance);
    final List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(storedVariableUpdateInstances)
      .singleElement()
      .extracting(
        VariableUpdateInstanceDto::getName,
        VariableUpdateInstanceDto::getType,
        VariableUpdateInstanceDto::getValue
      )
      .containsExactly("objectVar", OBJECT.getId(), null);
    assertThat(instanceVariables)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectVar", OBJECT.getId(), null)
      );
  }

  @SneakyThrows
  @Test
  public void largeObjectVariableCanBeStored() {
    // given an object variable with string fields that go above lucenes character limit
    final String longString1 = CharBuffer.allocate(8000).toString().replace('\0', 'a');
    final String longString2 = CharBuffer.allocate(2000).toString().replace('\0', 'b');
    final Map<String, Object> objectVar = new HashMap<>();
    objectVar.put("property1", longString1);
    objectVar.put("property2", longString2);
    final VariableDto objectVariableDto = variablesClient.createMapJsonObjectVariableDto(objectVar);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then the object variable is still imported correctly as the ignore_above setting is used for the
    // ProcessInstanceIndex
    assertThat(instanceVariables)
      .hasSize(3)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue
      )
      .withFailMessage("Large object variable was not imported correctly")
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectVar", objectVariableDto.getValue()),
        Tuple.tuple("objectVar.property1", longString1),
        Tuple.tuple("objectVar.property2", longString2)
      );
  }

  @Test
  public void variableUpdatesOnSameVariableDoNotCreateSeveralVariables() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
      .userTask()
      .endEvent()
      .done();
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariableNamesForProcessInstance(instanceDto);

    // then
    assertThat(variablesResponseDtos).hasSize(1);
  }

  @Test
  public void onlyTheLatestVariableValueUpdateIsImported() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo1")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "bar")
      .endEvent()
      .done();
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("bar");
  }

  @Test
  public void variablesForCompletedProcessInstancesAreFinalResult() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
        .camundaOutputParameter("anotherVar", "1")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "bar")
        .camundaOutputParameter("anotherVar", "2")
      .endEvent()
      .done();
    // @formatter:on

    ProcessInstanceEngineDto instanceDto = engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("bar");

    // when
    requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("anotherVar");
    requestDto.setType(STRING);
    variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("2");
  }

  @Test
  public void oldVariableUpdatesAreOverwritten() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
        .userTask()
        .serviceTask()
          .camundaExpression("${true}")
          .camundaOutputParameter("stringVar", "foo")
        .userTask()
      .endEvent()
      .done();
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    engineIntegrationExtension.finishAllRunningUserTasks(instanceDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);
    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("foo");
  }

  @Test
  public void deletingARuntimeVariableAlsoRemovesItFromOptimize() {
    // given
    BpmnModelInstance processModel = getSingleUserTaskDiagram();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    engineIntegrationExtension.deleteVariableInstanceForProcessInstance("stringVar", instanceDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues).isEmpty();
  }

  @SneakyThrows
  @Test
  public void variablesWithoutDefinitionKeyCanBeImported() {
    // given
    final BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);

    engineDatabaseExtension.removeProcessDefinitionKeyFromAllHistoricVariableUpdates();

    // when
    importAllEngineEntitiesFromScratch();
    final List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariableNamesForProcessInstance(instanceDto);
    final List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
  }

  @SneakyThrows
  private VariableDto createListJsonObjectVariableDto(final List<Object> variable) {
    return variablesClient.createJsonObjectVariableDto(
      new ObjectMapper()
        .setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT))
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValueAsString(variable),
      "java.util.ArrayList"
    );
  }

  private Map<String, Object> createPersonVariableWithAllTypes() {
    Map<String, Object> person = new HashMap<>();
    person.put("name", "Pond");
    person.put("age", 28);
    person.put("IQ", 99999999999999L);
    person.put("birthday", new Date(OffsetDateTime.parse("1992-11-17T00:00:00+01:00").toInstant().toEpochMilli()));
    person.put("muscleMassInPercent", 99.9);
    person.put("deceased", false);
    person.put("hands", (short) 2);
    person.put("likes", List.of("optimize", "garlic"));
    Map<String, Boolean> skillsMap = new HashMap<>();
    skillsMap.put("read", true);
    skillsMap.put("write", false);
    person.put("skills", skillsMap);
    return person;
  }

  private List<ProcessVariableNameResponseDto> getVariableNamesForProcessInstance(ProcessInstanceEngineDto instanceDto) {
    return variablesClient
      .getProcessVariableNames(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
  }

  private List<SimpleProcessVariableDto> getVariablesForProcessInstance(ProcessInstanceEngineDto instanceDto) {
    return elasticSearchIntegrationTestExtension.getProcessInstanceById(instanceDto.getId())
      .map(ProcessInstanceDto::getVariables)
      .orElse(Collections.emptyList());
  }

  private List<VariableUpdateInstanceDto> getStoredVariableUpdateInstances() throws JsonProcessingException {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = new ArrayList<>();
    for (SearchHit searchHitFields : response.getHits()) {
      final VariableUpdateInstanceDto variableUpdateInstanceDto = embeddedOptimizeExtension.getObjectMapper().readValue(
        searchHitFields.getSourceAsString(), VariableUpdateInstanceDto.class);
      storedVariableUpdateInstances.add(variableUpdateInstanceDto);
    }
    return storedVariableUpdateInstances;
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceProcessTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.deleteHistoricProcessInstance(firstProcInst.getId());
    engineIntegrationExtension.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  private void assertThatEveryFlowNodeWasExecuted4Times(String processDefinitionKey) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    List<MapResultEntryDto> flowNodeIdToExecutionFrequency = result.getFirstMeasureData();
    for (MapResultEntryDto frequency : flowNodeIdToExecutionFrequency) {
      assertThat(frequency.getValue()).isEqualTo(4L);
    }
  }

  private static Stream<Arguments> primitiveObjectVariableScenarios() {
    return Stream.of(
      Arguments.of("\"someString\"", "java.lang.String", STRING, "someString"),
      Arguments.of("5", "java.lang.Integer", DOUBLE, "5.0"),
      Arguments.of("true", "java.lang.Boolean", BOOLEAN, "true")
    );
  }

}
