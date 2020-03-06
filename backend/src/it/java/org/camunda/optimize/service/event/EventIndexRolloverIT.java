/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.event;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.events.rollover.EventIndexRolloverService;
import org.camunda.optimize.service.util.configuration.EventIndexRolloverConfiguration;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;

public class EventIndexRolloverIT extends AbstractIT {

  private static final int NUMBER_OF_EVENTS_IN_BATCH = 10;
  private static final String EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER = "-000002";
  private static final String EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER = "-000003";

  @BeforeEach
  public void before() {
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
  }

  @AfterEach
  public void cleanUpEventIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalEventIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new EventIndex()
    );
  }

  @Test
  public void testRolloverNoCamundaActivitiesImported() {
    // given
    ingestExternalEvents();
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    final List<String> rolledOverIndices = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(rolledOverIndices).containsExactly(EXTERNAL_EVENTS_INDEX_NAME);
  }

  @Test
  public void testRolloverIncludingCamundaActivityIndex() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    final List<String> rolledOverIndices = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(rolledOverIndices).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processInstanceEngineDto.getProcessDefinitionKey()
    );
  }

  @Test
  public void testMultipleRolloversSuccessful() throws IOException {
    // given
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    ingestExternalEvents();
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    final CamundaActivityEventIndex camundaActivityIndex =
      new CamundaActivityEventIndex(processInstanceEngineDto.getProcessDefinitionKey());
    final List<String> rolledOverIndicesFirstRollover = getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithExternalEventWriteAliasFirstRollover =
      getAllIndicesWithEventWriteAlias(EXTERNAL_EVENTS_INDEX_NAME);
    List<String> indicesWithCamundaActivityWriteAliasFirstRollover =
      getAllIndicesWithEventWriteAlias(camundaActivityIndex.getIndexName());

    // then
    assertThat(rolledOverIndicesFirstRollover).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processInstanceEngineDto.getProcessDefinitionKey()
    );
    assertThat(indicesWithExternalEventWriteAliasFirstRollover).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithCamundaActivityWriteAliasFirstRollover).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(getAllStoredExternalEvents())
      .hasSize(NUMBER_OF_EVENTS_IN_BATCH);
    // The process start, start event and start of user tasks have been imported, so we expect 3 in ES
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(3);

    // when
    ingestExternalEvents();
    importNextCamundaEventsForProcessInstance(processInstanceEngineDto);
    final List<String> rolledOverIndicesSecondRollover = getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithExternalEventWriteAliasSecondRollover =
      getAllIndicesWithEventWriteAlias(EXTERNAL_EVENTS_INDEX_NAME);
    List<String> indicesWithCamundaActivityWriteAliasSecondRollover =
      getAllIndicesWithEventWriteAlias(camundaActivityIndex.getIndexName());

    // then
    assertThat(rolledOverIndicesSecondRollover).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processInstanceEngineDto.getProcessDefinitionKey()
    );
    assertThat(indicesWithExternalEventWriteAliasSecondRollover).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER));
    assertThat(indicesWithCamundaActivityWriteAliasSecondRollover).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER));
    assertThat(getAllStoredExternalEvents())
      .hasSize(NUMBER_OF_EVENTS_IN_BATCH * 2);
    // Over the two imports, we expect 3 activities to be imported in the first and 6 in the second
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(9);
  }

  @Test
  public void testRolloverConditionsNotMet() {
    // given
    ingestExternalEvents();
    importCamundaEvents();

    // when
    final List<String> rolledOverIndices = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(rolledOverIndices).isEmpty();
  }

  @Test
  public void aliasAssociatedWithCorrectIndexAfterRollover() throws IOException {
    // given
    ingestExternalEvents();
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    final CamundaActivityEventIndex camundaActivityIndex =
      new CamundaActivityEventIndex(processInstanceEngineDto.getProcessDefinitionKey());
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithExternalEventWriteAlias = getAllIndicesWithEventWriteAlias(EXTERNAL_EVENTS_INDEX_NAME);
    List<String> indicesWithCamundaActivityWriteAlias = getAllIndicesWithEventWriteAlias(camundaActivityIndex.getIndexName());

    // then
    assertThat(indicesWithExternalEventWriteAlias).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithCamundaActivityWriteAlias).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
  }

  @Test
  public void noDataLossAfterRollover() {
    // given
    ingestExternalEvents();
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(getAllStoredExternalEvents()).hasSize(NUMBER_OF_EVENTS_IN_BATCH);
    // The process start, start event and start of user tasks have been imported, so we expect 3 in ES
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(3);
  }

  @Test
  public void searchAliasPointsToAllIndicesAfterRollover() {
    // given
    ingestExternalEvents();
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    getEventIndexRolloverService().triggerRollover();
    ingestExternalEvents();
    importNextCamundaEventsForProcessInstance(processInstanceEngineDto);

    // then there are 2 * EXPECTED_NUMBER_OF_EVENTS present (half in the old index and half in the new)
    assertThat(getAllStoredExternalEvents()).hasSize(NUMBER_OF_EVENTS_IN_BATCH * 2);
    // Over the two imports, we expect 3 activities to be imported in the first and 6 in the second
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(9);
  }

  @Test
  public void rolloversDisabledWhenEventBasedProcessFeatureDisabled() {
    // given
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);

    // when
    final List<String> rolledOverIndices = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(rolledOverIndices).isEmpty();
  }

  @Test
  public void aliasAssociatedWithCorrectIndexAfterRolloverOfMultipleCamundaActivityIndices() throws IOException {
    // given
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    final ProcessInstanceEngineDto firstProcessInstanceEngineDto = importCamundaEvents();
    final CamundaActivityEventIndex firstCamundaActivityIndex =
      new CamundaActivityEventIndex(firstProcessInstanceEngineDto.getProcessDefinitionKey());
    final ProcessInstanceEngineDto secondProcessInstanceEngineDto = importCamundaEvents();
    final CamundaActivityEventIndex secondCamundaActivityIndex =
      new CamundaActivityEventIndex(secondProcessInstanceEngineDto.getProcessDefinitionKey());

    // when
    getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithFirstCamundaActivityWriteAlias = getAllIndicesWithEventWriteAlias(firstCamundaActivityIndex.getIndexName());
    List<String> indicesWithSecondCamundaActivityWriteAlias = getAllIndicesWithEventWriteAlias(secondCamundaActivityIndex.getIndexName());

    // then
    assertThat(indicesWithFirstCamundaActivityWriteAlias).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithSecondCamundaActivityWriteAlias).hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
  }

  private EventIndexRolloverService getEventIndexRolloverService() {
    return embeddedOptimizeExtension.getEventIndexRolloverService();
  }

  private EventIndexRolloverConfiguration getEventIndexRolloverConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration();
  }

  private List<EventDto> getAllStoredExternalEvents() {
    return elasticSearchIntegrationTestExtension.getAllStoredExternalEvents();
  }

  private List<CamundaActivityEventDto> getAllStoredCamundaActivityEventsForDefinitionKey(final String indexName) {
    return elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEvents(indexName);
  }

  private void ingestExternalEvents() {
    final List<CloudEventDto> eventDtos = IntStream.range(0, NUMBER_OF_EVENTS_IN_BATCH)
      .mapToObj(operand -> eventClient.createCloudEventDto())
      .collect(toList());

    eventClient.ingestEventBatch(eventDtos);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void importNextCamundaEventsForProcessInstance(final ProcessInstanceEngineDto processInstanceEngineDto) {
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private ProcessInstanceEngineDto importCamundaEvents() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("start_event_id")
      .userTask("some_user_task")
      .endEvent("end_event_id")
      .done();
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      modelInstance);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return processInstanceEngineDto;
  }

  private List<String> getAllIndicesWithEventWriteAlias(String indexName) throws IOException {
    final String eventAliasNameWithPrefix = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(
        indexName);

    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(eventAliasNameWithPrefix);
    Map<String, Set<AliasMetaData>> aliasMap = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getHighLevelClient()
      .indices()
      .getAlias(aliasesRequest, RequestOptions.DEFAULT)
      .getAliases();

    return aliasMap.keySet()
      .stream()
      .filter(index -> aliasMap.get(index).removeIf(AliasMetaData::writeIndex))
      .collect(toList());
  }

}
