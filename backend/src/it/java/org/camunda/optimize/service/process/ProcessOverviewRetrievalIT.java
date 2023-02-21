/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.MAGIC_LINK_TEMPLATE;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.SPIDERMAN_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessOverviewRetrievalIT extends AbstractIT {

  private final String FIRST_PROCESS_DEFINITION_KEY = "firstDefKey";
  private final String SECOND_PROCESS_DEFINITION_KEY = "secondDefKey";

  @Test
  public void getProcessOverview_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessOverviewRequest(null)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getProcessOverview_noProcessDefinitionFound() {
    // when
    final List<ProcessOverviewResponseDto> processes = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessOverviewRequest(null)
      .executeAndReturnList(ProcessOverviewResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(processes).isEmpty();
  }

  @Test
  public void getProcessOverview_userCanOnlySeeAuthorizedProcesses() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessOverviewResponseDto> processes = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessOverviewRequest(null)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ProcessOverviewResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(processes).isEmpty();
  }

  @Test
  public void getProcessOverview_eventBasedProcessedNotShownOnProcessOverview() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      SECOND_PROCESS_DEFINITION_KEY, new IdentityDto(DEFAULT_USERNAME, IdentityType.USER));
    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(null);

    // then
    assertThat(processes).isEmpty();
  }

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
    setProcessOwner(firstDef.getKey(), DEFAULT_USERNAME);
    setProcessOwner(secondDef.getKey(), KERMIT_USER);

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

  @Test
  public void getProcessOverview_sortByProcessNameWhenProcessHasNoName() {
    // given
    final String firstProcessName = "aProcessName";
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch(firstProcessName, "a");
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch(null, "b");
    final String thirdProcessName = "cProcessName";
    addProcessDefinitionWithGivenNameAndKeyToElasticSearch(thirdProcessName, "c");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessOverviewSorter sorter = new ProcessOverviewSorter(
      ProcessOverviewResponseDto.Fields.processDefinitionName,
      SortOrder.ASC
    );
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(sorter);

    // then
    assertThat(processes).hasSize(3).extracting(ProcessOverviewResponseDto::getProcessDefinitionName)
      .containsExactly(firstProcessName, "b", thirdProcessName);
  }

  @ParameterizedTest
  @MethodSource("getDefinitionNameAndExpectedSortOrder")
  public void getProcessOverview_processesGetOrderedByOwnerAndDefinitionNameWhenOwnerNameIsMissingFromSomeDefinitions(final SortOrder sortOrder,
                                                                                                                      final List<String> processDefinitionKeys) {
    // given
    authorizationClient.addSpidermanUserAndGrantAccessToOptimize();
    processDefinitionKeys.forEach(this::deploySimpleProcessDefinition);
    importAllEngineEntitiesFromScratch();
    setProcessOwner("firstDefWithOwner", DEFAULT_USERNAME);
    setProcessOwner("secondDefWithOwner", SPIDERMAN_USER);
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
    setProcessOwner("a", DEFAULT_USERNAME);
    setProcessOwner("b", DEFAULT_USERNAME);

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
  public void magicLinkUrlContainsExpectedContent() {
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
        // No suffix
        String.format(MAGIC_LINK_TEMPLATE, "", FIRST_PROCESS_DEFINITION_KEY, FIRST_PROCESS_DEFINITION_KEY)));
  }

  @Test
  public void getProcessDigests() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      DEFAULT_USERNAME,
      "kermit",
      RESOURCE_TYPE_USER
    );
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(FIRST_PROCESS_DEFINITION_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram("anotherProcess"));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      FIRST_PROCESS_DEFINITION_KEY,
      DEFAULT_USERNAME,
      new ProcessDigestRequestDto(false)
    );
    processOverviewClient.updateProcess(
      "anotherProcess",
      "kermit",
      new ProcessDigestRequestDto(false)
    );

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(2)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getDigest)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          FIRST_PROCESS_DEFINITION_KEY,
          new ProcessDigestResponseDto(false)
        ),
        Tuple.tuple(
          "anotherProcess",
          new ProcessDigestResponseDto(false)
        )
      );
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

  private void setProcessOwner(final String processDefKey, final String ownerId) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(processDefKey, new ProcessUpdateDto(ownerId, new ProcessDigestRequestDto()))
      .execute();
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private static Stream<Arguments> getSortOrderAndExpectedDefinitionNameComparator() {
    return Stream.of(
      Arguments.of(
        SortOrder.ASC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName)
      ),
      Arguments.of(
        null,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName)
      ),
      Arguments.of(
        SortOrder.DESC,
        Comparator.comparing(ProcessOverviewResponseDto::getProcessDefinitionName).reversed()
      )
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
