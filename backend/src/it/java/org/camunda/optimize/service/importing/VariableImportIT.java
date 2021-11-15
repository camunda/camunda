/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ObjectVariableDto;
import org.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import javax.ws.rs.core.MediaType;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
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

  @RegisterExtension
  protected final LogCapturer variableImportServiceLogCapturer = LogCapturer.create()
    .forLevel(Level.INFO)
    .captureForType(VariableUpdateInstanceImportService.class);

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
  public void objectVariablesAreFlattened() {
    // given
    final Map<String, Object> person = createPersonVariableWithAllTypes();
    final ObjectVariableDto objectVariableDto = createMapJsonObjectVariableDto(person);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);
    final List<Tuple> expectedFlattenedVars = List.of(
      Tuple.tuple("objectVar.name", STRING.getId(), "Pond"),
      Tuple.tuple("objectVar.age", LONG.getId(), "28"),
      Tuple.tuple("objectVar.IQ", LONG.getId(), "99999999999999"),
      Tuple.tuple("objectVar.birthday", DATE.getId(), "1992-11-17T00:00:00.000+0100"),
      Tuple.tuple("objectVar.muscleMassInPercent", DOUBLE.getId(), "99.9"),
      Tuple.tuple("objectVar.deceased", BOOLEAN.getId(), "false"),
      Tuple.tuple("objectVar.hands", LONG.getId(), "2"),
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
      .hasSize(10)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrderElementsOf(expectedFlattenedVars);
    assertThat(storedVariableUpdateInstances)
      .hasSize(10)
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
      .containsExactlyInAnyOrderElementsOf(expectedFlattenedVars);
  }

  @SneakyThrows
  @Test
  public void flattenedObjectVariablesAreUpdated() {
    // given an instance with an object variable
    final Map<String, Object> objectVar = new HashMap<>();
    objectVar.put("name", "Pond");
    objectVar.put("age", "28");
    objectVar.put("likes", List.of("optimize", "garlic"));
    ObjectVariableDto objectVariableDto = createMapJsonObjectVariableDto(objectVar);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVariableDto);
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(getSingleUserTaskDiagram(), variables);
    importAllEngineEntitiesFromScratch();

    // and the variable is updated while the instance is running
    objectVar.put("age", "29");
    objectVar.put("likes", List.of("optimize", "garlic", "tofu"));
    objectVariableDto = createMapJsonObjectVariableDto(objectVar);
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
        Tuple.tuple("objectVar.name", STRING.getId(), "Pond", 2L),
        Tuple.tuple("objectVar.age", STRING.getId(), "29", 2L),
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
    final ObjectVariableDto objectVariableDto = createListJsonObjectVariableDto(List.of(pet1, pet2));
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectListVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then
    assertThat(instanceVariables)
      .singleElement()
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactly("objectListVar._listSize", LONG.getId(), "2");
  }

  @SneakyThrows
  @Test
  public void objectVariablesThatAreStringsAreImported() {
    // given
    final String variableValue = "\"someString\"";
    final ObjectVariableDto objectVariableDto =
      createJsonObjectVariableDto(variableValue, "java.lang.String");
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectStringVar", objectVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceProcessTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();
    final List<SimpleProcessVariableDto> instanceVariables = getVariablesForProcessInstance(instanceDto);

    // then
    assertThat(instanceVariables)
      .singleElement()
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactly("objectStringVar", STRING.getId(), "someString");
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
    final ObjectVariableDto objectVariableDto = createJsonObjectVariableDto(
      new ObjectMapper().setDateFormat(objectVarDateFormat).writeValueAsString(objectVar),
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
      .singleElement()
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactly("objectVar.dateProperty", DATE.getId(), "2021-11-01T05:05:00.000+0100");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void objectVariableValueIsFetchedDependingOnConfig(final boolean includeObjectVariableValue) {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .setEngineImportVariableIncludeObjectVariableValue(includeObjectVariableValue);

    final ObjectVariableDto objectVariableDto = createMapJsonObjectVariableDto(null);
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
  public void variablesCanHaveNullValue() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariablesWithNullValues();
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
  private ObjectVariableDto createMapJsonObjectVariableDto(final Map<String, Object> variable) {
    return createJsonObjectVariableDto(
      new ObjectMapper().setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT)).writeValueAsString(variable),
      "java.util.HashMap"
    );
  }

  @SneakyThrows
  private ObjectVariableDto createListJsonObjectVariableDto(final List<Object> variable) {
    return createJsonObjectVariableDto(
      new ObjectMapper().setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT)).writeValueAsString(variable),
      "java.util.ArrayList"
    );
  }

  @SneakyThrows
  private ObjectVariableDto createJsonObjectVariableDto(final String value,
                                                        final String objectTypeName) {
    ObjectVariableDto objectVariableDto = new ObjectVariableDto();
    objectVariableDto.setType(EngineConstants.VARIABLE_TYPE_OBJECT);
    objectVariableDto.setValue(value);
    ObjectVariableDto.ValueInfo info = new ObjectVariableDto.ValueInfo();
    info.setObjectTypeName(objectTypeName);
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);
    objectVariableDto.setValueInfo(info);
    return objectVariableDto;
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

}
