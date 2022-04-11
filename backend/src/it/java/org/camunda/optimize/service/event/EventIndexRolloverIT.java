/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.event;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.events.rollover.EventIndexRolloverService;
import org.camunda.optimize.service.util.configuration.IndexRolloverConfiguration;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class EventIndexRolloverIT extends AbstractIT {

  private static final int NUMBER_OF_EVENTS_IN_BATCH = 10;
  private static final String EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER = "-000002";
  private static final String EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER = "-000003";

  @BeforeEach
  public void before() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getEventImport()
      .setEnabled(true);
  }

  @BeforeEach
  @AfterEach
  public void cleanUpEventIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalEventIndices();
    elasticSearchIntegrationTestExtension.deleteAllVariableUpdateInstanceIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new EventIndex()
    );
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new VariableUpdateInstanceIndex()
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
    assertThat(rolledOverIndices).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME
    );
  }

  @Test
  public void testRolloverIncludingAllRelevantIndices() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    final List<String> rolledOverIndices = getEventIndexRolloverService().triggerRollover();

    // then
    assertThat(rolledOverIndices).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processInstanceEngineDto.getProcessDefinitionKey(),
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME
    );
  }

  @Test
  public void testMultipleRolloversSuccessful() throws IOException {
    // given
    getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);

    // when
    ingestExternalEvents();
    importVariableUpdateInstances(3);
    final ProcessInstanceEngineDto processInstanceEngineDto = importCamundaEvents();
    final CamundaActivityEventIndex camundaActivityIndex =
      new CamundaActivityEventIndex(processInstanceEngineDto.getProcessDefinitionKey());
    final List<String> rolledOverIndicesFirstRollover = getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithExternalEventWriteAliasFirstRollover =
      getAllIndicesWithWriteAlias(EXTERNAL_EVENTS_INDEX_NAME);
    List<String> indicesWithCamundaActivityWriteAliasFirstRollover =
      getAllIndicesWithWriteAlias(camundaActivityIndex.getIndexName());
    List<String> indicesWithVariableUpdateInstanceWriteAliasFirstRollover =
      getAllIndicesWithWriteAlias(VARIABLE_UPDATE_INSTANCE_INDEX_NAME);

    // then
    assertThat(rolledOverIndicesFirstRollover).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processInstanceEngineDto.getProcessDefinitionKey(),
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME
    );
    assertThat(indicesWithExternalEventWriteAliasFirstRollover).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithCamundaActivityWriteAliasFirstRollover).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithVariableUpdateInstanceWriteAliasFirstRollover).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));

    assertThat(getAllStoredExternalEvents())
      .hasSize(NUMBER_OF_EVENTS_IN_BATCH);
    // The process start, start event and start of user tasks have been imported, so we expect 3 in ES
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(3);
    assertThat(getAllStoredVariableUpdateInstanceDtos())
      .hasSize(3);

    // when
    ingestExternalEvents();
    importVariableUpdateInstances(3);
    importNextCamundaEventsForProcessInstance(processInstanceEngineDto);
    final List<String> rolledOverIndicesSecondRollover = getEventIndexRolloverService().triggerRollover();
    List<String> indicesWithExternalEventWriteAliasSecondRollover =
      getAllIndicesWithWriteAlias(EXTERNAL_EVENTS_INDEX_NAME);
    List<String> indicesWithCamundaActivityWriteAliasSecondRollover =
      getAllIndicesWithWriteAlias(camundaActivityIndex.getIndexName());
    List<String> indicesWithVariableUpdateInstanceWriteAliasSecondRollover =
      getAllIndicesWithWriteAlias(VARIABLE_UPDATE_INSTANCE_INDEX_NAME);

    // then
    assertThat(rolledOverIndicesSecondRollover).containsExactlyInAnyOrder(
      EXTERNAL_EVENTS_INDEX_NAME,
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processInstanceEngineDto.getProcessDefinitionKey(),
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME
    );
    assertThat(indicesWithExternalEventWriteAliasSecondRollover).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER));
    assertThat(indicesWithCamundaActivityWriteAliasSecondRollover).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER));
    assertThat(indicesWithVariableUpdateInstanceWriteAliasSecondRollover).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_SECOND_ROLLOVER));
    assertThat(getAllStoredExternalEvents())
      .hasSize(NUMBER_OF_EVENTS_IN_BATCH * 2);
    // Over the two imports, we expect 3 activities to be imported in the first and 5 in the second
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(8);
    assertThat(getAllStoredVariableUpdateInstanceDtos())
      .hasSize(6);
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
    List<String> indicesWithExternalEventWriteAlias = getAllIndicesWithWriteAlias(EXTERNAL_EVENTS_INDEX_NAME);
    List<String> indicesWithCamundaActivityWriteAlias =
      getAllIndicesWithWriteAlias(camundaActivityIndex.getIndexName());
    List<String> indicesWithVariableUpdateInstanceWriteAlias =
      getAllIndicesWithWriteAlias(VARIABLE_UPDATE_INSTANCE_INDEX_NAME);

    // then
    assertThat(indicesWithExternalEventWriteAlias).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithCamundaActivityWriteAlias).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithVariableUpdateInstanceWriteAlias).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
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
    // Over the two imports, we expect 3 activities to be imported in the first and 5 in the second
    assertThat(getAllStoredCamundaActivityEventsForDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(8);
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
    List<String> indicesWithFirstCamundaActivityWriteAlias =
      getAllIndicesWithWriteAlias(firstCamundaActivityIndex.getIndexName());
    List<String> indicesWithSecondCamundaActivityWriteAlias = getAllIndicesWithWriteAlias(
      secondCamundaActivityIndex.getIndexName());

    // then
    assertThat(indicesWithFirstCamundaActivityWriteAlias).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
    assertThat(indicesWithSecondCamundaActivityWriteAlias).hasSize(1)
      .singleElement()
      .satisfies(indexName -> assertThat(indexName).contains(EXPECTED_SUFFIX_AFTER_FIRST_ROLLOVER));
  }

  private EventIndexRolloverService getEventIndexRolloverService() {
    return embeddedOptimizeExtension.getEventIndexRolloverService();
  }

  private IndexRolloverConfiguration getEventIndexRolloverConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration();
  }

  private List<EventDto> getAllStoredExternalEvents() {
    return elasticSearchIntegrationTestExtension.getAllStoredExternalEvents();
  }

  private List<CamundaActivityEventDto> getAllStoredCamundaActivityEventsForDefinitionKey(final String indexName) {
    return elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(indexName);
  }

  private List<VariableUpdateInstanceDto> getAllStoredVariableUpdateInstanceDtos() {
    return elasticSearchIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos();
  }

  private void ingestExternalEvents() {
    final List<CloudEventRequestDto> eventDtos = IntStream.range(0, NUMBER_OF_EVENTS_IN_BATCH)
      .mapToObj(operand -> ingestionClient.createCloudEventDto())
      .collect(toList());

    ingestionClient.ingestEventBatch(eventDtos);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void importNextCamundaEventsForProcessInstance(final ProcessInstanceEngineDto processInstanceEngineDto) {
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromLastIndex();
  }

  private void importVariableUpdateInstances(final int count) {
    IntStream.range(0, count)
      .mapToObj(operand -> createSimpleVariableUpdateInstanceDto(String.valueOf(operand)))
      .forEach(variableUpdateInstanceDto -> elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
        VARIABLE_UPDATE_INSTANCE_INDEX_NAME,
        variableUpdateInstanceDto.getInstanceId(),
        variableUpdateInstanceDto
      ));

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private VariableUpdateInstanceDto createSimpleVariableUpdateInstanceDto(final String instanceId) {
    return VariableUpdateInstanceDto.builder()
      .instanceId(instanceId)
      .name("someName")
      .processInstanceId("someProcessInstanceId")
      .build();
  }

  private ProcessInstanceEngineDto importCamundaEvents() {
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension
      .deployAndStartProcess(getSingleUserTaskDiagram(
        "akey",
        "start_event_id",
        "end_event_id",
        "some_user_task"
      ));
    importAllEngineEntitiesFromScratch();
    return processInstanceEngineDto;
  }

  private List<String> getAllIndicesWithWriteAlias(String indexName) throws IOException {
    final String aliasNameWithPrefix = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getIndexNameService()
      .getOptimizeIndexAliasForIndex(indexName);

    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasNameWithPrefix);
    Map<String, Set<AliasMetadata>> aliasMap = embeddedOptimizeExtension.getOptimizeElasticClient()
      .getAlias(aliasesRequest).getAliases();

    return aliasMap.keySet()
      .stream()
      .filter(index -> aliasMap.get(index).removeIf(AliasMetadata::writeIndex))
      .collect(toList());
  }

}
