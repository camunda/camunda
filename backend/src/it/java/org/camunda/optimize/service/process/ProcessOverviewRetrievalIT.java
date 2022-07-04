/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.awaitility.Awaitility;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.ProcessOverviewService.APP_CUE_DASHBOARD_SUFFIX;
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.MAGIC_LINK_TEMPLATE;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.SPIDERMAN_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessOverviewRetrievalIT extends AbstractIT {

  private final String FIRST_PROCESS_DEFINITION_KEY = "firstDefKey";
  private final String SECOND_PROCESS_DEFINITION_KEY = "secondDefKey";
  private final String DEFAULT_USERNAME = "DEFAULT_USERNAME";
  private final ProcessOwnerDto PROCESS_OWNER_DTO = new ProcessOwnerDto("DEFAULT_USERNAME");

  @Test
  public void getProcessOverview_fetchedAccordingToAscendingOrderWhenSortOrderIsNull() {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessOverviewSorter sorter = new ProcessOverviewSorter(
      ProcessOverviewResponseDto.Fields.processDefinitionName,
      null
    );
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(sorter);

    // then sort in ascending order
    assertThat(processes).hasSize(2)
      .isSortedAccordingTo(Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName));
  }

  @ParameterizedTest
  @MethodSource("getSortCriteriaAndExpectedComparator")
  public void getProcessOverview_sortByValidSortFields(final String sortBy,
                                                       final SortOrder sortingOrder,
                                                       final Comparator<ProcessOverviewResponseDto> comparator) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    final ProcessDefinitionEngineDto firstDef = deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    final ProcessDefinitionEngineDto secondDef = deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();
    setProcessOwner(firstDef.getKey(), PROCESS_OWNER_DTO);
    ProcessOwnerDto kermitOwnerDto = new ProcessOwnerDto(KERMIT_USER);
    setProcessOwner(secondDef.getKey(), kermitOwnerDto);

    // when
    ProcessOverviewSorter sorter = new ProcessOverviewSorter(sortBy, sortingOrder);
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(sorter);

    // then
    assertThat(processes).hasSize(2).isSortedAccordingTo(comparator);
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedDefinitionKeyComparator")
  public void getProcessOverview_sortByKeyWhenNamesAreIdentical(final SortOrder sortOrder,
                                                                final Comparator<ProcessOverviewResponseDto> comparator) {
    // given
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "a");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "b");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch("sameName", "c");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessOverviewSorter sorter = new ProcessOverviewSorter(
      ProcessOverviewResponseDto.Fields.processDefinitionName,
      sortOrder
    );
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(sorter);

    // then
    assertThat(processes).hasSize(3).isSortedAccordingTo(comparator);
  }

  @ParameterizedTest
  @MethodSource("getDefinitionNameAndExpectedSortOrder")
  public void getProcessOverview_processesGetOrderedByOwnerAndDefinitionNameWhenOwnerNameIsMissingFromSomeDefinitions(final SortOrder sortOrder,
                                                                                                                      final List<String> processDefinitionKeys) {
    // given
    authorizationClient.addSpidermanUserAndGrantAccessToOptimize();
    processDefinitionKeys.forEach(key -> deploySimpleProcessDefinition(key));
    importAllEngineEntitiesFromScratch();
    setProcessOwner("firstDefWithOwner", new ProcessOwnerDto(DEFAULT_USERNAME));
    setProcessOwner("secondDefWithOwner", new ProcessOwnerDto(SPIDERMAN_USER));
    importAllEngineEntitiesFromLastIndex();

    // when
    ProcessOverviewSorter sorter = new ProcessOverviewSorter(
      ProcessOverviewResponseDto.Fields.owner,
      sortOrder
    );
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(sorter);

    // then
    assertThat(processes).hasSize(4)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey)
      .isEqualTo(processDefinitionKeys);
  }

  @ParameterizedTest
  @MethodSource("getSortOrderAndExpectedDefinitionNameComparator")
  public void getProcessOverview_processesOrderedByProcessDefinitionNameWhenTheyHaveSameOwner(final SortOrder sortOrder,
                                                                                              final Comparator<ProcessOverviewResponseDto> comparator) {
    // given
    deploySimpleProcessDefinition("a");
    deploySimpleProcessDefinition("b");
    importAllEngineEntitiesFromScratch();
    setProcessOwner("a", new ProcessOwnerDto(DEFAULT_USERNAME));
    setProcessOwner("b", new ProcessOwnerDto(DEFAULT_USERNAME));

    // when
    ProcessOverviewSorter sorter = new ProcessOverviewSorter(
      ProcessOverviewResponseDto.Fields.owner,
      sortOrder
    );
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(sorter);

    // then
    assertThat(processes).hasSize(2).isSortedAccordingTo(comparator);
  }

  @Test
  public void getProcessOverview_invalidSortParameter() {
    // given
    ProcessOverviewSorter sorter = new ProcessOverviewSorter("invalid", SortOrder.ASC);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessOverviewRequest(sorter)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void magicLinkHasAppCueSuffixIfItsClickedForTheFirstTime() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(FIRST_PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessOverviewResponseDto> overviews = processOverviewClient.getProcessOverviews();

    // then
    assertThat(overviews).filteredOn(process -> process.getProcessDefinitionKey()
        .equals(FIRST_PROCESS_DEFINITION_KEY))
      .singleElement()
      .satisfies(process -> assertThat(process.getLinkToDashboard()).isEqualTo(
        String.format(MAGIC_LINK_TEMPLATE, FIRST_PROCESS_DEFINITION_KEY, FIRST_PROCESS_DEFINITION_KEY)
          + APP_CUE_DASHBOARD_SUFFIX));
  }

  @Test
  public void magicLinkHasNoAppCueSuffixIfItHasBeenClickedBefore() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(SECOND_PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    // "Click" the link once
    entitiesClient.getEntityNames(SECOND_PROCESS_DEFINITION_KEY, SECOND_PROCESS_DEFINITION_KEY, null, null);
    // Wait until everything is created
    Awaitility.given().ignoreExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(collectionClient.getCollectionById(SECOND_PROCESS_DEFINITION_KEY)).isNotNull());

    // when
    final List<ProcessOverviewResponseDto> overviews = processOverviewClient.getProcessOverviews();

    // then
    assertThat(overviews).filteredOn(process -> process.getProcessDefinitionKey()
        .equals(SECOND_PROCESS_DEFINITION_KEY))
      .singleElement()
      .satisfies(process -> assertThat(process.getLinkToDashboard()).isEqualTo(
        // No suffix
        String.format(MAGIC_LINK_TEMPLATE, SECOND_PROCESS_DEFINITION_KEY, SECOND_PROCESS_DEFINITION_KEY)));
  }

  private ProcessDefinitionOptimizeDto createProcessDefinition(String definitionKey, String name) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(definitionKey)
      .name(name)
      .version("1")
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml("xml")
      .build();
  }

  private void addProcessDefinitionWithGivenNameAndKeyToElasticSearch(String name, String key) {
    final DefinitionOptimizeResponseDto definition = createProcessDefinition(key, name);
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      new ProcessDefinitionIndex().getIndexName(),
      Map.of(definition.getId(), definition)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void setProcessOwner(final String processDefKey, final ProcessOwnerDto processOwnerDto) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(processDefKey, processOwnerDto)
      .execute();
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private static Stream<Arguments> getSortOrderAndExpectedDefinitionNameComparator() {
    return Stream.of(
      Arguments.of(
        SortOrder.ASC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName)),
      Arguments.of(
        null,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName)),
      Arguments.of(
        SortOrder.DESC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName).reversed())
    );
  }

  private static Stream<Arguments> getDefinitionNameAndExpectedSortOrder() {
    return Stream.of(
      Arguments.of(
        SortOrder.ASC,
        List.of("secondDefWithOwner", "firstDefWithOwner", "fourthDefWithOwner", "thirdDefWithOwner")
      ),
      Arguments.of(
        null,
        List.of("secondDefWithOwner", "firstDefWithOwner", "fourthDefWithOwner", "thirdDefWithOwner")
      ),
      Arguments.of(
        SortOrder.DESC,
        List.of("thirdDefWithOwner", "fourthDefWithOwner", "firstDefWithOwner", "secondDefWithOwner")
      )
    );
  }

  private static Stream<Arguments> getSortCriteriaAndExpectedComparator() {
    return Stream.of(
      Arguments.of(
        ProcessOverviewResponseDto.Fields.processDefinitionName,
        SortOrder.ASC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName)
      ),
      Arguments.of(
        ProcessOverviewResponseDto.Fields.processDefinitionName,
        null,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName)
      ),
      Arguments.of(
        ProcessOverviewResponseDto.Fields.processDefinitionName,
        SortOrder.DESC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName).reversed()
      ),
      Arguments.of(
        ProcessOverviewResponseDto.Fields.owner,
        SortOrder.ASC,
        Comparator.comparing(
          (ProcessOverviewResponseDto processOverviewResponseDto) -> processOverviewResponseDto.getOwner().getName(),
          Comparator.nullsLast(Comparator.naturalOrder())
        )
      ),
      Arguments.of(
        ProcessOverviewResponseDto.Fields.owner,
        null,
        Comparator.comparing(
          (ProcessOverviewResponseDto processOverviewResponseDto) -> processOverviewResponseDto.getOwner().getName(),
          Comparator.nullsLast(Comparator.naturalOrder())
        )
      ),
      Arguments.of(
        ProcessOverviewResponseDto.Fields.owner,
        SortOrder.DESC,
        Comparator.comparing(
          (ProcessOverviewResponseDto processOverviewResponseDto) -> processOverviewResponseDto.getOwner().getName(),
          Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed()
      )
    );
  }

  private static Stream<Arguments> getSortOrderAndExpectedDefinitionKeyComparator() {
    return Stream.of(
      Arguments.of(SortOrder.ASC, Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionKey)),
      Arguments.of(null, Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionKey)),
      Arguments.of(
        SortOrder.DESC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionKey).reversed()
      )
    );
  }

}
