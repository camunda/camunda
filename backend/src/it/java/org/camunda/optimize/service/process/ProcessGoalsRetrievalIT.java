/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessGoalSorter;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class ProcessGoalsRetrievalIT extends AbstractProcessGoalsIT {

  @Test
  public void getProcessGoals_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessGoalsRequest()
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getProcessGoals_noProcessDefinitionGoalsFound() {
    // when
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals();

    // then
    assertThat(processGoalsDtos).isEmpty();
  }

  @Test
  public void getProcessGoals_processDefinitionGoalsFetchedAccordingToAscendingOrderWhenSortOrderIsNull() {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalsResponseDto.Fields.processName, null);
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals(sorter);

    // then sort in ascending order
    assertThat(processGoalsDtos).hasSize(2)
      .isSortedAccordingTo(Comparator.comparing(ProcessGoalsResponseDto::getProcessName))
      .containsExactly(
        new ProcessGoalsResponseDto(
          FIRST_PROCESS_DEFINITION_KEY,
          FIRST_PROCESS_DEFINITION_KEY,
          null,
          Collections.emptyList()
        ),
        new ProcessGoalsResponseDto(
          SECOND_PROCESS_DEFINITION_KEY,
          SECOND_PROCESS_DEFINITION_KEY,
          null,
          Collections.emptyList()
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
    List<ProcessGoalsDto> processGoalsDtos = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessGoalsRequest()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ProcessGoalsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(processGoalsDtos).isEmpty();
  }

  @Test
  public void getProcessGoals_processesIncludeAnEventBasedProcess() {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    final EventProcessDefinitionDto eventProcessDefinitionDto = deployEventBasedProcessDefinition();

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals();

    // then
    assertThat(processGoalsDtos).hasSize(2)
      .isSortedAccordingTo(Comparator.comparing(ProcessGoalsResponseDto::getProcessName))
      .containsExactly(
        new ProcessGoalsResponseDto(
          FIRST_PROCESS_DEFINITION_KEY,
          FIRST_PROCESS_DEFINITION_KEY,
          null,
          Collections.emptyList()
        ),
        new ProcessGoalsResponseDto(
          eventProcessDefinitionDto.getName(),
          eventProcessDefinitionDto.getKey(),
          null,
          Collections.emptyList()
        )
      );
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedProcessNameComparator")
  public void getProcessGoals_sortByProcessName(final SortOrder sortingOrder,
                                                final Comparator<ProcessGoalsResponseDto> comparator) {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalsResponseDto.Fields.processName, sortingOrder);
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals(sorter);

    // then
    assertThat(processGoalsDtos).hasSize(2).isSortedAccordingTo(comparator);
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedProcessNameComparator")
  public void getProcessGoals_useDefinitionKeyForSortOrderForProcessWithNoName(final SortOrder sortOrder,
                                                                               final Comparator<ProcessGoalsResponseDto> comparator) {
    // given
    ProcessDefinitionEngineDto processDefinitionWithName = deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    String processDefinitionKeyForProcessWithNoName = addProcessDefinitionWithNoNameToElasticSearch();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalsResponseDto.Fields.processName, sortOrder);
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals(sorter);

    // then
    assertThat(processGoalsDtos).hasSize(2).isSortedAccordingTo(comparator)
      .extracting(ProcessGoalsResponseDto::getProcessName, ProcessGoalsResponseDto::getProcessDefinitionKey)
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
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals();

    // then
    assertThat(processGoalsDtos).hasSize(1).containsExactly(
      new ProcessGoalsResponseDto(
        FIRST_PROCESS_DEFINITION_KEY,
        FIRST_PROCESS_DEFINITION_KEY,
        null,
        Collections.emptyList()
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
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals();

    // then
    assertThat(processGoalsDtos).hasSize(1).containsExactly(
      new ProcessGoalsResponseDto(
        processDefinitionVersion1.getName(),
        processDefinitionVersion1.getKey(),
        null,
        Collections.emptyList()
      ));
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedDefinitionKeyComparator")
  public void getProcessGoals_sortByKeyWhenNamesAreIdentical(final SortOrder sortOrder,
                                                             final Comparator<ProcessGoalsResponseDto> comparator) {
    // given
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "a");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "b");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "c");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessGoalSorter sorter = new ProcessGoalSorter(ProcessGoalsResponseDto.Fields.processName, sortOrder);
    List<ProcessGoalsResponseDto> processGoalsDtos = getProcessGoals(sorter);

    // then
    assertThat(processGoalsDtos).hasSize(3).isSortedAccordingTo(comparator);
  }

  @ParameterizedTest
  @MethodSource("getInvalidSortByFields")
  public void getProcessGoals_invalidSortParameter(final String sortBy) {
    // given
    ProcessGoalSorter processGoalSorter = new ProcessGoalSorter(sortBy, SortOrder.ASC);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessGoalsRequest(processGoalSorter)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private List<ProcessGoalsResponseDto> getProcessGoals() {
    return getProcessGoals(null);
  }

  private List<ProcessGoalsResponseDto> getProcessGoals(ProcessGoalSorter sorter) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessGoalsRequest(sorter)
      .executeAndReturnList(ProcessGoalsResponseDto.class, Response.Status.OK.getStatusCode());
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
      Arguments.of(SortOrder.ASC, Comparator.comparing(ProcessGoalsResponseDto::getProcessName)),
      Arguments.of(null, Comparator.comparing(ProcessGoalsResponseDto::getProcessName)),
      Arguments.of(SortOrder.DESC, Comparator.comparing(ProcessGoalsResponseDto::getProcessName).reversed())
    );
  }

  private static Stream<String> getInvalidSortByFields() {
    return Stream.of("invalid", null);
  }

  private static Stream<Arguments> getSortOrderAndExpectedDefinitionKeyComparator() {
    return Stream.of(
      Arguments.of(SortOrder.ASC, Comparator.comparing(ProcessGoalsResponseDto::getProcessDefinitionKey)),
      Arguments.of(null, Comparator.comparing(ProcessGoalsResponseDto::getProcessDefinitionKey)),
      Arguments.of(SortOrder.DESC, Comparator.comparing(ProcessGoalsResponseDto::getProcessDefinitionKey).reversed())
    );
  }

}
