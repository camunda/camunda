/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessInstanceImportStartAndEndMappingOnSameFlowNodeScenariosIT extends AbstractEventProcessIT {

  private static Stream<Arguments> getScenarios() {
    return Stream.of(
      Arguments.of(
        startMapping(FIRST_EVENT_NAME),
        startAndEndMapping(SECOND_EVENT_NAME, THIRD_EVENT_NAME),
        startMapping(FOURTH_EVENT_NAME)
      ),
      Arguments.of(
        startMapping(FIRST_EVENT_NAME),
        startAndEndMapping(SECOND_EVENT_NAME, THIRD_EVENT_NAME),
        endMapping(FOURTH_EVENT_NAME)
      ),
      Arguments.of(
        endMapping(FIRST_EVENT_NAME),
        startAndEndMapping(SECOND_EVENT_NAME, THIRD_EVENT_NAME),
        endMapping(FOURTH_EVENT_NAME)
      ),
      Arguments.of(
        endMapping(FIRST_EVENT_NAME),
        startAndEndMapping(SECOND_EVENT_NAME, THIRD_EVENT_NAME),
        startMapping(FOURTH_EVENT_NAME)
      )
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
    final String fourthEventId = ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    createAndPublishMapping(startEventMapping, intermediateEventMapping, endEventMapping);

    // when
    executeImportCycle();

    // then
    assertProcessInstanceIsAsExpected(firstEventId, secondEventId, fourthEventId);
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
    final String fourthEventId = ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    createAndPublishMapping(startEventMapping, intermediateEventMapping, endEventMapping);

    // when
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();

    // then
    assertProcessInstanceIsAsExpected(firstEventId, secondEventId, fourthEventId);
  }

  @ParameterizedTest
  @MethodSource("getScenarios")
  public void instancesAreGeneratedAsExpectedWhenEventsAreInDifferentBatchesAndDifferentIngestionOrder(
    final EventMappingDto startEventMapping,
    final EventMappingDto intermediateEventMapping,
    final EventMappingDto endEventMapping) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);

    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    final String fourthEventId = ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    createAndPublishMapping(startEventMapping, intermediateEventMapping, endEventMapping);

    // when
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();

    // then
    assertProcessInstanceIsAsExpected(firstEventId, secondEventId, fourthEventId);
  }

  private void createAndPublishMapping(final EventMappingDto startEventMapping,
                                       final EventMappingDto intermediateEventMapping,
                                       final EventMappingDto endEventMapping) {
    final String eventProcessId = createEventProcessMappingFromEventMappings(
      startEventMapping, intermediateEventMapping, endEventMapping
    );
    eventProcessClient.publishEventProcessMapping(eventProcessId);
  }

  private void assertProcessInstanceIsAsExpected(final String firstEventId,
                                                 final String secondEventId,
                                                 final String fourthEventId) {
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.state, PROCESS_INSTANCE_STATE_COMPLETED)
            .hasFieldOrPropertyWithValue(
              ProcessInstanceDto.Fields.duration,
              Duration.between(FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME).toMillis()
            )
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.startDate, FIRST_EVENT_DATETIME)
            .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.endDate, FOURTH_EVENT_DATETIME)
            .extracting(ProcessInstanceDto::getFlowNodeInstances)
            .satisfies(events -> assertThat(events)
              .allSatisfy(simpleEventDto -> assertThat(simpleEventDto)
                .hasNoNullFieldsOrPropertiesExcept(NULLABLE_FLOW_NODE_FIELDS_TO_IGNORE))
              .extracting(
                FlowNodeInstanceDto::getFlowNodeInstanceId,
                FlowNodeInstanceDto::getFlowNodeId,
                FlowNodeInstanceDto::getStartDate,
                FlowNodeInstanceDto::getEndDate
              )
              .containsExactlyInAnyOrder(
                Tuple.tuple(
                  firstEventId,
                  BPMN_START_EVENT_ID,
                  FIRST_EVENT_DATETIME,
                  FIRST_EVENT_DATETIME
                ),
                Tuple.tuple(
                  secondEventId,
                  USER_TASK_ID_ONE,
                  SECOND_EVENT_DATETIME,
                  THIRD_EVENT_DATETIME
                ),
                Tuple.tuple(
                  fourthEventId,
                  BPMN_END_EVENT_ID,
                  FOURTH_EVENT_DATETIME,
                  FOURTH_EVENT_DATETIME
                )
              )
            );
        }
      );
  }
}
