/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.SimpleEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessInstanceImportSingleMappingScenariosIT extends AbstractEventProcessIT {

  private static Stream<Arguments> getScenarios() {
    return Stream.of(
      Arguments.of(startMapping(FIRST_EVENT_NAME), startMapping(SECOND_EVENT_NAME), startMapping(THIRD_EVENT_NAME)),
      Arguments.of(startMapping(FIRST_EVENT_NAME), startMapping(SECOND_EVENT_NAME), endMapping(THIRD_EVENT_NAME)),
      Arguments.of(startMapping(FIRST_EVENT_NAME), endMapping(SECOND_EVENT_NAME), startMapping(THIRD_EVENT_NAME)),
      Arguments.of(startMapping(FIRST_EVENT_NAME), endMapping(SECOND_EVENT_NAME), endMapping(THIRD_EVENT_NAME)),
      Arguments.of(endMapping(FIRST_EVENT_NAME), endMapping(SECOND_EVENT_NAME), endMapping(THIRD_EVENT_NAME)),
      Arguments.of(endMapping(FIRST_EVENT_NAME), endMapping(SECOND_EVENT_NAME), startMapping(THIRD_EVENT_NAME)),
      Arguments.of(endMapping(FIRST_EVENT_NAME), startMapping(SECOND_EVENT_NAME), endMapping(THIRD_EVENT_NAME)),
      Arguments.of(endMapping(FIRST_EVENT_NAME), startMapping(SECOND_EVENT_NAME), startMapping(THIRD_EVENT_NAME))
    );
  }

  @ParameterizedTest
  @MethodSource("getScenarios")
  public void instancesAreGeneratedAsExpectedWhenAllEventsAreInSameBatch(final EventMappingDto startEventMapping,
                                                                         final EventMappingDto intermediateEventMapping,
                                                                         final EventMappingDto endEventMapping) {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    createAndPublishMapping(startEventMapping, intermediateEventMapping, endEventMapping);

    // when
    executeImportCycle();

    // then
    assertProcessInstanceIsAsExpected(
      startEventMapping, intermediateEventMapping, endEventMapping, firstEventId, secondEventId, thirdEventId
    );
  }

  @ParameterizedTest
  @MethodSource("getScenarios")
  public void instancesAreGeneratedAsExpectedWhenEventsAreInDifferentBatches(final EventMappingDto startEventMapping,
                                                                             final EventMappingDto intermediateEventMapping,
                                                                             final EventMappingDto endEventMapping) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    createAndPublishMapping(startEventMapping, intermediateEventMapping, endEventMapping);

    // when
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();

    // then
    assertProcessInstanceIsAsExpected(
      startEventMapping, intermediateEventMapping, endEventMapping, firstEventId, secondEventId, thirdEventId
    );
  }

  @ParameterizedTest
  @MethodSource("getScenarios")
  public void instancesAreGeneratedAsExpectedWhenEventsAreInDifferentBatchesAndDifferentIngestionOrder(
    final EventMappingDto startEventMapping,
    final EventMappingDto intermediateEventMapping,
    final EventMappingDto endEventMapping) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    createAndPublishMapping(startEventMapping, intermediateEventMapping, endEventMapping);

    // when
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();

    // then
    assertProcessInstanceIsAsExpected(
      startEventMapping, intermediateEventMapping, endEventMapping, firstEventId, secondEventId, thirdEventId
    );
  }

  @ParameterizedTest
  @MethodSource("getScenarios")
  public void instancesAreGeneratedWithIntermediateEventMappingMissing(final EventMappingDto startEventMapping,
                                                                       final EventMappingDto intermediateEventMapping,
                                                                       final EventMappingDto endEventMapping) {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    // intermediate mapping is missing
    createAndPublishMapping(startEventMapping, null, endEventMapping);

    // when
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.state, PROCESS_INSTANCE_STATE_COMPLETED)
            .hasFieldOrPropertyWithValue(
              ProcessInstanceDto.Fields.duration,
              Duration.between(FIRST_EVENT_DATETIME, THIRD_EVENT_DATETIME).toMillis()
            )
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.startDate, FIRST_EVENT_DATETIME)
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.endDate, THIRD_EVENT_DATETIME)
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(events -> assertThat(events)
              .allSatisfy(simpleEventDto -> assertThat(simpleEventDto).hasNoNullFieldsOrProperties())
              .extracting(
                SimpleEventDto::getId,
                SimpleEventDto::getActivityId,
                SimpleEventDto::getStartDate,
                SimpleEventDto::getEndDate
              )
              .containsExactlyInAnyOrder(
                Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
                Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
              )
            );
        }
      );
  }

  @Test
  public void multipleActivityInstancesAreGeneratedPerUniqueEvent() {
    // given
    final String firstEventId1 = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    // this creates another event which is equal to the previous except for having a new unique id
    final String firstEventId2 = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);

    createAndPublishMapping(
      startMapping(FIRST_EVENT_NAME), startMapping(SECOND_EVENT_NAME), startMapping(THIRD_EVENT_NAME)
    );

    // when
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(events -> assertThat(events)
              .extracting(
                SimpleEventDto::getId,
                SimpleEventDto::getActivityId,
                SimpleEventDto::getStartDate
              )
              .containsExactlyInAnyOrder(
                Tuple.tuple(firstEventId1, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME),
                Tuple.tuple(firstEventId2, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME)
              )
            );
        }
      );
  }

  private void createAndPublishMapping(final EventMappingDto startEventMapping,
                                       final EventMappingDto intermediateEventMapping,
                                       final EventMappingDto endEventMapping) {
    final String eventProcessId = createEventProcessMappingFromEventMappings(
      startEventMapping, intermediateEventMapping, endEventMapping
    );
    eventProcessClient.publishEventProcessMapping(eventProcessId);
  }

  private void assertProcessInstanceIsAsExpected(final EventMappingDto startEventMapping,
                                                 final EventMappingDto intermediateEventMapping,
                                                 final EventMappingDto endEventMapping,
                                                 final String startEventId,
                                                 final String intermediateEventId,
                                                 final String endEventId) {
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.state, PROCESS_INSTANCE_STATE_COMPLETED)
            .hasFieldOrPropertyWithValue(
              ProcessInstanceDto.Fields.duration,
              Duration.between(FIRST_EVENT_DATETIME, THIRD_EVENT_DATETIME).toMillis()
            )
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.startDate, FIRST_EVENT_DATETIME)
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.endDate, THIRD_EVENT_DATETIME)
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(events -> assertThat(events)
              .allSatisfy(simpleEventDto -> assertThat(simpleEventDto).hasNoNullFieldsOrProperties())
              .extracting(
                SimpleEventDto::getId,
                SimpleEventDto::getActivityId,
                SimpleEventDto::getStartDate,
                SimpleEventDto::getEndDate
              )
              .containsExactlyInAnyOrder(
                Tuple.tuple(
                  startEventId,
                  BPMN_START_EVENT_ID,
                  FIRST_EVENT_DATETIME,
                  startEventMapping.getEnd() != null ? FIRST_EVENT_DATETIME : SECOND_EVENT_DATETIME
                ),
                Tuple.tuple(
                  intermediateEventId,
                  BPMN_INTERMEDIATE_EVENT_ID,
                  intermediateEventMapping.getStart() != null ? SECOND_EVENT_DATETIME : FIRST_EVENT_DATETIME,
                  intermediateEventMapping.getEnd() != null ? SECOND_EVENT_DATETIME : THIRD_EVENT_DATETIME
                ),
                Tuple.tuple(
                  endEventId,
                  BPMN_END_EVENT_ID,
                  endEventMapping.getStart() != null ? THIRD_EVENT_DATETIME : SECOND_EVENT_DATETIME,
                  THIRD_EVENT_DATETIME
                )
              )
            );
        }
      );
  }

}
