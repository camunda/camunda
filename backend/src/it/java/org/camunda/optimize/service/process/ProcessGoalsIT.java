/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.process;

import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.ProcessGoalDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessGoalSorter;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT.EXTERNAL_EVENT_GROUP;
import static org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT.EXTERNAL_EVENT_SOURCE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class ProcessGoalsIT extends AbstractIT {

  private static final String FIRST_PROCESS_DEFINITION_KEY = "firstProcessDefinition";
  private static final String SECOND_PROCESS_DEFINITION_KEY = "secondProcessDefinition";

  @Test
  public void getProcessGoals_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest()
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getProcessGoals_noProcessDefinitionGoalsFound() {
    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).isEmpty();
  }

  @Test
  public void getProcessGoals_processDefinitionGoalsFetchedAccordingToAscendingOrderWhenSortOrderIsNull() {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalDto.Fields.processName, null);
    List<ProcessGoalDto> processGoalDtos = getProcessGoals(sorter);

    // then sort in ascending order
    assertThat(processGoalDtos).hasSize(2)
      .isSortedAccordingTo(Comparator.comparing(ProcessGoalDto::getProcessName))
      .containsExactly(
        new ProcessGoalDto(
          FIRST_PROCESS_DEFINITION_KEY,
          FIRST_PROCESS_DEFINITION_KEY,
          Collections.emptyList(),
          null
        ),
        new ProcessGoalDto(
          SECOND_PROCESS_DEFINITION_KEY,
          SECOND_PROCESS_DEFINITION_KEY,
          Collections.emptyList(),
          null
        )
      );
  }

  @Test
  public void getProcessGoals_userCanOnlySeeAuthorizedProcesses() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ProcessGoalDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(processGoalDtos).isEmpty();
  }

  @Test
  public void getProcessGoals_processesIncludeAnEventBasedProcess() {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    final EventProcessDefinitionDto eventProcessDefinitionDto = deployEventBasedProcessDefinition();

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).hasSize(2)
      .isSortedAccordingTo(Comparator.comparing(ProcessGoalDto::getProcessName))
      .containsExactly(
        new ProcessGoalDto(
          FIRST_PROCESS_DEFINITION_KEY,
          FIRST_PROCESS_DEFINITION_KEY,
          Collections.emptyList(),
          null
        ),
        new ProcessGoalDto(
          eventProcessDefinitionDto.getKey(),
          eventProcessDefinitionDto.getName(),
          Collections.emptyList(),
          null
        )
      );
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedProcessNameComparator")
  public void getProcessGoals_sortByProcessName(final SortOrder sortingOrder,
                                                final Comparator<ProcessGoalDto> comparator) {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalDto.Fields.processName, sortingOrder);
    List<ProcessGoalDto> processGoalDtos = getProcessGoals(sorter);

    // then
    assertThat(processGoalDtos).hasSize(2).isSortedAccordingTo(comparator);
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedProcessNameComparator")
  public void getProcessGoals_useDefinitionKeyForSortOrderForProcessWithNoName(final SortOrder sortOrder,
                                                                               final Comparator<ProcessGoalDto> comparator) {
    // given
    ProcessDefinitionEngineDto processDefinitionWithName = deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    String processDefinitionKeyForProcessWithNoName = addProcessDefinitionWithNoNameToElasticSearch();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalDto.Fields.processName, sortOrder);
    List<ProcessGoalDto> processGoalDtos = getProcessGoals(sorter);

    // then
    assertThat(processGoalDtos).hasSize(2).isSortedAccordingTo(comparator)
      .extracting(ProcessGoalDto::getProcessName, ProcessGoalDto::getProcessDefinitionKey)
      .containsExactlyInAnyOrder(
        Tuple.tuple(processDefinitionWithName.getName(), processDefinitionWithName.getKey()),
        Tuple.tuple(processDefinitionKeyForProcessWithNoName, processDefinitionKeyForProcessWithNoName)
      );
  }

  @Test
  public void getProcessGoals_processGoalsGetReturnedOnceForMultipleTenants() {
    // given
    BpmnModelInstance bpmnModelInstance = getSingleUserTaskDiagram(FIRST_PROCESS_DEFINITION_KEY);
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      bpmnModelInstance,
      "firstTenant"
    );
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      bpmnModelInstance,
      "secondTenant"
    );
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).hasSize(1).containsExactly(
      new ProcessGoalDto(
        FIRST_PROCESS_DEFINITION_KEY,
        FIRST_PROCESS_DEFINITION_KEY,
        Collections.emptyList(),
        null
      )
    );
  }

  @Test
  public void getProcessGoals_processGoalsGetReturnedOnceForMultipleProcessVersions() {
    // given
    final DefinitionOptimizeResponseDto processDefinitionVersion1 = createProcessDefinition(
      "1",
      FIRST_PROCESS_DEFINITION_KEY,
      "someName"
    );
    final DefinitionOptimizeResponseDto processDefinitionVersion2 = createProcessDefinition(
      "2",
      FIRST_PROCESS_DEFINITION_KEY,
      "someName"
    );
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new ProcessDefinitionIndex().getIndexName(),
      Map.of(
        processDefinitionVersion1.getId(),
        processDefinitionVersion1,
        processDefinitionVersion2.getId(),
        processDefinitionVersion2
      )
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).hasSize(1).containsExactly(
      new ProcessGoalDto(
        processDefinitionVersion1.getKey(),
        processDefinitionVersion1.getName(),
        Collections.emptyList(),
        null
      ));
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedDefinitionKeyComparator")
  public void getProcessGoals_sortByKeyWhenNamesAreIdentical(final SortOrder sortOrder,
                                                             final Comparator<ProcessGoalDto> comparator) {
    // given
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "a");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "b");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "c");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalDto.Fields.processName, sortOrder);
    List<ProcessGoalDto> processGoalDtos = getProcessGoals(sorter);

    // then
    assertThat(processGoalDtos).hasSize(3).isSortedAccordingTo(comparator);
  }

  @ParameterizedTest
  @MethodSource("getInvalidSortByFields")
  public void getProcessGoals_invalidSortParameter(final String sortBy) {
    // given
    ProcessGoalSorter processGoalSorter = new ProcessGoalSorter(sortBy, SortOrder.ASC);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest(processGoalSorter)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto() {
    return buildSimpleEventProcessMappingDto(
      buildEventMappingDto(START_EVENT),
      buildEventMappingDto(END_EVENT)
    );
  }

  private EventMappingDto buildEventMappingDto(final String endEvent) {
    return EventMappingDto.builder()
      .end(EventTypeDto.builder()
             .group(EXTERNAL_EVENT_GROUP)
             .source(EXTERNAL_EVENT_SOURCE)
             .eventName(endEvent)
             .build())
      .build();
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto(final EventMappingDto startEventMapping,
                                                                   final EventMappingDto endEventMapping) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(START_EVENT, startEventMapping);
    eventMappings.put(END_EVENT, endEventMapping);
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      eventMappings, "myEventProcess", createTwoEventAndOneTaskActivitiesProcessDefinitionXml()
    );
  }

  @SneakyThrows
  private void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler()
      .runImportRound(true)
      .get(10, TimeUnit.SECONDS);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void publishMappingAndExecuteImport(final String eventProcessId) {
    eventProcessClient.publishEventProcessMapping(eventProcessId);
    // we execute the import cycle so the event instance index gets created
    executeImportCycle();
  }

  private void ingestTestEvent(final String eventId,
                               final String eventName,
                               final OffsetDateTime eventTimestamp) {
    embeddedOptimizeExtension.getEventService()
      .saveEventBatch(
        Collections.singletonList(
          EventDto.builder()
            .id(eventId)
            .eventName(eventName)
            .timestamp(eventTimestamp.toInstant().toEpochMilli())
            .traceId("myTraceId1")
            .group(EXTERNAL_EVENT_GROUP)
            .source(EXTERNAL_EVENT_SOURCE)
            .build()
        )
      );
  }

  private List<ProcessGoalDto> getProcessGoals() {
    return getProcessGoals(null);
  }

  private List<ProcessGoalDto> getProcessGoals(ProcessGoalSorter sorter) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest(sorter)
      .executeAndReturnList(ProcessGoalDto.class, Response.Status.OK.getStatusCode());
  }

  private EventProcessDefinitionDto deployEventBasedProcessDefinition() {
    ingestTestEvent(IdGenerator.getNextId(), START_EVENT, OffsetDateTime.now());
    ingestTestEvent(IdGenerator.getNextId(), END_EVENT, OffsetDateTime.now());
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    publishMappingAndExecuteImport(eventProcessDefinitionKey);
    executeImportCycle();
    final EventProcessDefinitionDto eventProcessDefinitionDto = new EventProcessDefinitionDto();
    eventProcessDefinitionDto.setName(simpleEventProcessMappingDto.getName());
    eventProcessDefinitionDto.setKey(eventProcessDefinitionKey);
    return eventProcessDefinitionDto;
  }

  private ProcessDefinitionOptimizeDto createProcessDefinition() {
    return createProcessDefinition("1", "hasNoName", null);
  }

  private ProcessDefinitionOptimizeDto createProcessDefinition(String version, String definitionKey, String name) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(definitionKey)
      .name(name)
      .version(version)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml("xml")
      .build();
  }

  private String addProcessDefinitionWithNoNameToElasticSearch() {
    final DefinitionOptimizeResponseDto def = createProcessDefinition();
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new ProcessDefinitionIndex().getIndexName(),
      Map.of(def.getId(), def)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return def.getKey();
  }

  private void addProcessDefinitionWithGivenNameAndKeyToElasticSearch(String name, String key) {
    final DefinitionOptimizeResponseDto definition = createProcessDefinition("1", name, key);
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new ProcessDefinitionIndex().getIndexName(),
      Map.of(definition.getId(), definition)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private static Stream<Arguments> getSortOrderAndExpectedProcessNameComparator() {
    return Stream.of(
      Arguments.of(SortOrder.ASC, Comparator.comparing(ProcessGoalDto::getProcessName)),
      Arguments.of(null, Comparator.comparing(ProcessGoalDto::getProcessName)),
      Arguments.of(SortOrder.DESC, Comparator.comparing(ProcessGoalDto::getProcessName).reversed())
    );
  }

  private static Stream<String> getInvalidSortByFields() {
    return Stream.of("invalid", null);
  }

  private static Stream<Arguments> getSortOrderAndExpectedDefinitionKeyComparator() {
    return Stream.of(
      Arguments.of(SortOrder.ASC, Comparator.comparing(ProcessGoalDto::getProcessDefinitionKey)),
      Arguments.of(null, Comparator.comparing(ProcessGoalDto::getProcessDefinitionKey)),
      Arguments.of(SortOrder.DESC, Comparator.comparing(ProcessGoalDto::getProcessDefinitionKey).reversed())
    );
  }

  @SneakyThrows
  private static String createTwoEventAndOneTaskActivitiesProcessDefinitionXml() {
    return convertBpmnModelToXmlString(getSingleUserTaskDiagram("aProcessName"));
  }

  private static String convertBpmnModelToXmlString(final BpmnModelInstance bpmnModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return xmlOutput.toString(StandardCharsets.UTF_8);
  }

}
