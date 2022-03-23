/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessDefinitionImportIT extends AbstractEventProcessIT {

  @Test
  public void eventProcessDefinitionIsAvailableAfterProcessReachedPublishState() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    // when
    publishEventBasedProcess(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    // then
    final Optional<EventProcessDefinitionDto> eventProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId);
    assertThat(eventProcessDefinition)
      .get()
      .usingRecursiveComparison()
      .isEqualTo(
        EventProcessDefinitionDto.eventProcessBuilder()
          .id(expectedProcessDefinitionId)
          .key(eventProcessMappingId)
          .version("1")
          .versionTag(null)
          .name(simpleEventProcessMappingDto.getName())
          .tenantId(null)
          .bpmn20Xml(simpleEventProcessMappingDto.getXml())
          .deleted(false)
          .userTaskNames(ImmutableMap.of(USER_TASK_ID_ONE, USER_TASK_ID_ONE))
          .flowNodeData(ImmutableList.of(
            new FlowNodeDataDto(BPMN_END_EVENT_ID, BPMN_END_EVENT_ID, END_EVENT_TYPE),
            new FlowNodeDataDto(USER_TASK_ID_ONE, USER_TASK_ID_ONE, USER_TASK_TYPE),
            new FlowNodeDataDto(BPMN_START_EVENT_ID, BPMN_START_EVENT_ID, START_EVENT_TYPE)
          ))
          .build()
      );
  }

  @Test
  public void eventProcessDefinitionIsAvailableForMultiplePublishedProcesses() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);

    // when
    publishEventBasedProcess(eventProcessMappingId1);
    publishEventBasedProcess(eventProcessMappingId2);

    final String expectedProcessDefinitionId1 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId1);
    final String expectedProcessDefinitionId2 = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    // then
    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId1))
      .isNotEmpty();

    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId2))
      .isNotEmpty();
  }

  @Test
  public void eventProcessDefinitionIsAvailableRepublishAndPreviousIsGone() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId = createAndPublishEventProcess(simpleEventProcessMappingDto);
    final String previousProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    // when
    final String newName = "updatedProcess";
    simpleEventProcessMappingDto.setName(newName);
    eventProcessClient.updateEventProcessMapping(eventProcessMappingId, simpleEventProcessMappingDto);
    publishEventBasedProcess(eventProcessMappingId);

    final String newProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    // then
    final Optional<EventProcessDefinitionDto> previousProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(previousProcessDefinitionId);
    final Optional<EventProcessDefinitionDto> newProcessDefinition =
      getEventProcessDefinitionFromElasticsearch(newProcessDefinitionId);

    assertThat(previousProcessDefinition).isEmpty();

    assertThat(newProcessDefinition)
      .get()
      .hasFieldOrPropertyWithValue(DefinitionOptimizeResponseDto.Fields.id, newProcessDefinitionId)
      .hasFieldOrPropertyWithValue(DefinitionOptimizeResponseDto.Fields.name, newName);
  }

  @Test
  public void eventProcessDefinitionRepublishCausesReportToGetUpdated() {
    // given a published event process
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId = createAndPublishEventProcess(simpleEventProcessMappingDto);

    final String reportId1 = createEventProcessReport(eventProcessMappingId, simpleEventProcessMappingDto.getXml());
    final String reportId2 = createEventProcessReport(eventProcessMappingId, simpleEventProcessMappingDto.getXml());
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    // process is republished with new XML
    final String newXml = createTwoEventAndOneTaskActivitiesProcessDefinitionXml();
    simpleEventProcessMappingDto.setXml(newXml);
    eventProcessClient.updateEventProcessMapping(eventProcessMappingId, simpleEventProcessMappingDto);
    publishEventBasedProcess(eventProcessMappingId);

    // then the definition XML in existing reports for that event process are updated as well
    final SingleProcessReportDefinitionRequestDto reportDefinition1 =
      reportClient.getSingleProcessReportById(reportId1);
    assertThat(reportDefinition1.getData().getConfiguration().getXml())
      .isEqualTo(newXml);
    final SingleProcessReportDefinitionRequestDto reportDefinition2 =
      reportClient.getSingleProcessReportById(reportId2);
    assertThat(reportDefinition2.getData().getConfiguration().getXml())
      .isEqualTo(newXml);
  }

  @Test
  public void eventProcessDefinitionRepublishCausesNoSideEffectToOtherProcessReports() {
    // given a published event process
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);

    final EventProcessMappingDto simpleEventProcessMappingDto1 = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId1 = createAndPublishEventProcess(simpleEventProcessMappingDto1);

    final String reportId1 = createEventProcessReport(eventProcessMappingId1, simpleEventProcessMappingDto1.getXml());
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when another event process gets published
    final EventProcessMappingDto simpleEventProcessMappingDto2 = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    final String eventProcessMappingId2 = createAndPublishEventProcess(simpleEventProcessMappingDto2);
    // and republished with new xml
    simpleEventProcessMappingDto2.setXml(createTwoEventAndOneTaskActivitiesProcessDefinitionXml());
    eventProcessClient.updateEventProcessMapping(eventProcessMappingId2, simpleEventProcessMappingDto2);
    publishEventBasedProcess(eventProcessMappingId2);

    // then the definition XML a the report on the first event process is not affected
    final SingleProcessReportDefinitionRequestDto reportDefinition1 =
      reportClient.getSingleProcessReportById(reportId1);
    assertThat(reportDefinition1.getData().getConfiguration().getXml())
      .isEqualTo(simpleEventProcessMappingDto1.getXml());
  }

  @ParameterizedTest(name = "Event Process Definition is deleted on {0}.")
  @MethodSource("cancelOrDeleteAction")
  public void eventProcessDefinitionIsDeletedOn(final String actionName,
                                                final BiConsumer<EventProcessClient, String> action) {
    // given
    final String startedEventName = STARTED_EVENT;
    ingestTestEvent(startedEventName);
    final String finishedEventName = FINISHED_EVENT;
    ingestTestEvent(finishedEventName);

    final String eventProcessMappingId = createSimpleEventProcessMapping(startedEventName, finishedEventName);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId);

    executeImportCycle();
    executeImportCycle();

    // when
    action.accept(eventProcessClient, eventProcessMappingId);
    executeImportCycle();

    // then
    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId)).isEmpty();
  }

  @ParameterizedTest(name = "Only expected instance index is deleted on {0}, others are still present.")
  @MethodSource("cancelOrDeleteAction")
  public void otherEventProcessDefinitionIsNotAffectedOn(final String actionName,
                                                         final BiConsumer<EventProcessClient, String> action) {
    // given
    final String startedEventName = STARTED_EVENT;
    ingestTestEvent(startedEventName);
    final String finishedEventName = FINISHED_EVENT;
    ingestTestEvent(finishedEventName);

    final String eventProcessMappingId1 = createSimpleEventProcessMapping(startedEventName, finishedEventName);
    final String eventProcessMappingId2 = createSimpleEventProcessMapping(startedEventName, finishedEventName);

    eventProcessClient.publishEventProcessMapping(eventProcessMappingId1);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId2);
    final String expectedProcessDefinitionId = getEventPublishStateIdForEventProcessMappingId(eventProcessMappingId2);

    executeImportCycle();
    executeImportCycle();

    // when
    action.accept(eventProcessClient, eventProcessMappingId1);
    executeImportCycle();

    // then
    assertThat(getEventProcessDefinitionFromElasticsearch(expectedProcessDefinitionId))
      .isNotEmpty();
  }

  private String createAndPublishEventProcess(final EventProcessMappingDto simpleEventProcessMappingDto) {
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    publishEventBasedProcess(eventProcessMappingId);

    return eventProcessMappingId;
  }

  private void publishEventBasedProcess(final String eventProcessMappingId) {
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    // two cycles needed as the definition gets published with the next cycle after publish finished
    executeImportCycle();
    executeImportCycle();
  }

  private String createEventProcessReport(final String eventProcessMappingId, final String definitionXml) {
    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null, eventProcessMappingId, Collections.emptyList()
      );
    reportDefinitionDto.getData().getConfiguration().setXml(definitionXml);
    return reportClient.createSingleProcessReport(reportDefinitionDto);
  }

}
