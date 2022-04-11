/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.event.process.EventCorrelationStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.MappedEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessInstanceImportAdjacentEventUpdateCorrelationIT extends AbstractEventProcessIT {

  private static Stream<Arguments> getImportScenarios() {
    return Stream.of(
      Arguments.of(
        "singleImportCycle",
        (Consumer<EventProcessInstanceImportAdjacentEventUpdateCorrelationIT>) AbstractEventProcessIT::executeImportCycle
      ),
      Arguments.of(
        "importIdempotenceScenario",
        (Consumer<EventProcessInstanceImportAdjacentEventUpdateCorrelationIT>) testInstance -> {
          // this simulates the same event getting imported more than once
          // this could happen in a real world scenario if the progress does not get updated before Optimize is shutdown
          testInstance.executeImportCycle();
          testInstance.resetEventProcessInstanceImportProgress();
          testInstance.executeImportCycle();
        }
      )
    );
  }

  @ParameterizedTest(name = "import scenario: {0}")
  @MethodSource("getImportScenarios")
  public void taskWithoutEndMappingGetsAssignedEndDateByNextAdjacentEvent(final String scenarioName,
                                                                          final Consumer<EventProcessInstanceImportAdjacentEventUpdateCorrelationIT> importScenario) {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createTwoEventAndOneTaskActivitiesProcessDefinitionXml());

    // when
    importScenario.accept(this);

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          )
          .satisfies(
            eventProcessInstanceDto -> {
              assertThat(eventProcessInstanceDto.getFlowNodeInstances())
                .satisfies(flowNodeInstances -> assertThat(flowNodeInstances)
                  .allSatisfy(flowNodeInstance -> assertThat(flowNodeInstance)
                    .hasNoNullFieldsOrPropertiesExcept(NULLABLE_FLOW_NODE_FIELDS_TO_IGNORE))
                  .extracting(
                    FlowNodeInstanceDto::getFlowNodeInstanceId,
                    FlowNodeInstanceDto::getFlowNodeId,
                    FlowNodeInstanceDto::getStartDate,
                    FlowNodeInstanceDto::getEndDate
                  )
                  .containsExactlyInAnyOrder(
                    Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
                    Tuple.tuple(secondEventId, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
                    Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
                  )
                );
              // all correlations have been recorded as expected
              assertThat(eventProcessInstanceDto.getCorrelatedEventsById())
                .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
                .containsValues(
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(firstEventId),
                    MappedEventType.END, ImmutableSet.of(firstEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(secondEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(thirdEventId),
                    MappedEventType.END, ImmutableSet.of(secondEventId, thirdEventId)
                  ))
                );
            }
          );
      });
  }

  @Test
  public void taskWithoutEndMappingGetsAssignedEndDateByNextAdjacentEvent_nextAdjacentEventUpdateAlsoUpdatesTaskEndDate() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createTwoEventAndOneTaskActivitiesProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime updatedThirdEventTime = THIRD_EVENT_DATETIME.plusSeconds(10);
    ingestTestEvent(thirdEventId, THIRD_EVENT_NAME, updatedThirdEventTime);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          )
          .satisfies(
            eventProcessInstanceDto -> {
              assertThat(eventProcessInstanceDto.getFlowNodeInstances())
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
                    Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
                    Tuple.tuple(secondEventId, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, updatedThirdEventTime),
                    Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, updatedThirdEventTime, updatedThirdEventTime)
                  )
                );
              // all correlations have been recorded as expected
              assertThat(eventProcessInstanceDto.getCorrelatedEventsById())
                .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
                .containsValues(
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(firstEventId),
                    MappedEventType.END, ImmutableSet.of(firstEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(secondEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(thirdEventId),
                    MappedEventType.END, ImmutableSet.of(secondEventId, thirdEventId)
                  ))
                );
            }
          );
      });
  }

  @Test
  public void taskWithoutEndMappingGetsAssignedEndDateByNextAdjacentEvent_nextAdjacentEventUpdateAlsoUpdatesTaskEndDate_evenIfItResultsInNegativeDuration() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createTwoEventAndOneTaskActivitiesProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime updatedThirdEventTime = SECOND_EVENT_DATETIME.minusSeconds(1);
    ingestTestEvent(thirdEventId, THIRD_EVENT_NAME, updatedThirdEventTime);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          )
          .satisfies(
            eventProcessInstanceDto -> {
              assertThat(eventProcessInstanceDto.getFlowNodeInstances())
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
                    Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
                    Tuple.tuple(secondEventId, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, updatedThirdEventTime),
                    Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, updatedThirdEventTime, updatedThirdEventTime)
                  )
                );
              // all correlations have been recorded as expected
              assertThat(eventProcessInstanceDto.getCorrelatedEventsById())
                .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
                .containsValues(
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(firstEventId),
                    MappedEventType.END, ImmutableSet.of(firstEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(secondEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(thirdEventId),
                    MappedEventType.END, ImmutableSet.of(secondEventId, thirdEventId)
                  ))
                );
            }
          );
      });
  }

  @ParameterizedTest(name = "opening gateway type: {0}, closing gateway type: {1}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void taskWithoutEndMappingBeforeClosingExclusiveGateway_nextAdjacentEventUpdateAlsoUpdatesTaskEndDate(
    final String openingGatewayType,
    final String closingGatewayType,
    final String bpmnXml) {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();
    final OffsetDateTime updatedThirdEventTimestamp = THIRD_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(thirdEventId, THIRD_EVENT_NAME, updatedThirdEventTimestamp);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          );
        assertThat(processInstanceDto.getFlowNodeInstances())
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
                firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME
              ),
              Tuple.tuple(
                SPLITTING_GATEWAY_ID + "_1",
                SPLITTING_GATEWAY_ID,
                FIRST_EVENT_DATETIME,
                openingGatewayType.equalsIgnoreCase(EVENT_BASED_GATEWAY_TYPE) ? SECOND_EVENT_DATETIME :
                  FIRST_EVENT_DATETIME
              ),
              Tuple.tuple(
                secondEventId, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, updatedThirdEventTimestamp
              ),
              Tuple.tuple(
                MERGING_GATEWAY_ID + "_1", MERGING_GATEWAY_ID, updatedThirdEventTimestamp, updatedThirdEventTimestamp
              ),
              Tuple.tuple(
                thirdEventId, BPMN_END_EVENT_ID, updatedThirdEventTimestamp, updatedThirdEventTimestamp
              )
            )
          );
        // all correlations have been recorded as expected
        assertThat(processInstanceDto.getCorrelatedEventsById())
          .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
          .containsValues(
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(firstEventId),
              MappedEventType.END, ImmutableSet.of(firstEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(secondEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(thirdEventId),
              MappedEventType.END, ImmutableSet.of(secondEventId, thirdEventId)
            ))
          );
      });
  }

  @Test
  public void tasksWithoutEndMappingBeforeClosingParallelGateway_nextAdjacentEventUpdateAlsoUpdatesTasksEndDate() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    final String fourthEventId = ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, endMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createParallelGatewayProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime newFourthEventTimestamp = FOURTH_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(fourthEventId, FOURTH_EVENT_NAME, newFourthEventTimestamp);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getPendingFlowNodeInstanceUpdates())
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .isEmpty();
        assertThat(processInstanceDto.getFlowNodeInstances())
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
                firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
              Tuple.tuple(
                SPLITTING_GATEWAY_ID + "_1", SPLITTING_GATEWAY_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME
              ),
              Tuple.tuple(
                secondEventId, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, newFourthEventTimestamp
              ),
              Tuple.tuple(
                thirdEventId, USER_TASK_ID_TWO, THIRD_EVENT_DATETIME, newFourthEventTimestamp
              ),
              Tuple.tuple(
                MERGING_GATEWAY_ID + "_1", MERGING_GATEWAY_ID, newFourthEventTimestamp, newFourthEventTimestamp
              ),
              Tuple.tuple(
                fourthEventId, BPMN_END_EVENT_ID, newFourthEventTimestamp, newFourthEventTimestamp
              )
            )
          );
        // all correlations have been recorded as expected
        assertThat(processInstanceDto.getCorrelatedEventsById())
          .containsOnlyKeys(firstEventId, secondEventId, thirdEventId, fourthEventId)
          .containsValues(
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(firstEventId),
              MappedEventType.END, ImmutableSet.of(firstEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(secondEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(thirdEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(fourthEventId),
              MappedEventType.END, ImmutableSet.of(fourthEventId, secondEventId, thirdEventId)
            ))
          );
      });
  }

  @Test
  public void taskWithoutEndMappingBeforeClosingExclusiveGateway_consecutiveGateways_nextAdjacentEventUpdateAlsoUpdatesTaskEndDate() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(
      eventMappings,
      createExclusiveGatewayProcessDefinitionWithConsecutiveGatewaysXml()
    );

    // when
    executeImportCycle();
    final OffsetDateTime updatedThirdEventTimestamp = THIRD_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(thirdEventId, THIRD_EVENT_NAME, updatedThirdEventTimestamp);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getPendingFlowNodeInstanceUpdates()).isEmpty();
        assertThat(processInstanceDto.getFlowNodeInstances())
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
                firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME
              ),
              Tuple.tuple(
                SPLITTING_GATEWAY_ID + "_1", SPLITTING_GATEWAY_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME
              ),
              Tuple.tuple(
                secondEventId, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, updatedThirdEventTimestamp
              ),
              Tuple.tuple(
                MERGING_GATEWAY_ID + "_1", MERGING_GATEWAY_ID, updatedThirdEventTimestamp, updatedThirdEventTimestamp
              ),
              Tuple.tuple(
                MERGING_GATEWAY_ID_TWO + "_1",
                MERGING_GATEWAY_ID_TWO,
                updatedThirdEventTimestamp,
                updatedThirdEventTimestamp
              ),
              Tuple.tuple(
                thirdEventId, BPMN_END_EVENT_ID, updatedThirdEventTimestamp, updatedThirdEventTimestamp
              )
            )
          );
        // all correlations have been recorded as expected
        assertThat(processInstanceDto.getCorrelatedEventsById())
          .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
          .containsValues(
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(firstEventId),
              MappedEventType.END, ImmutableSet.of(firstEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(secondEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(thirdEventId),
              MappedEventType.END, ImmutableSet.of(secondEventId, thirdEventId)
            ))
          );
      });
  }

  @ParameterizedTest(name = "import scenario: {0}")
  @MethodSource("getImportScenarios")
  public void taskWithoutStartMappingGetsAssignedStartDateByPreviousAdjacentEvent(final String scenarioName,
                                                                                  final Consumer<EventProcessInstanceImportAdjacentEventUpdateCorrelationIT> importScenario) {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, endMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createTwoEventAndOneTaskActivitiesProcessDefinitionXml());

    // when
    importScenario.accept(this);

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          )
          .satisfies(
            eventProcessInstanceDto -> {
              assertThat(eventProcessInstanceDto.getFlowNodeInstances())
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
                    Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
                    Tuple.tuple(secondEventId, USER_TASK_ID_ONE, FIRST_EVENT_DATETIME, SECOND_EVENT_DATETIME),
                    Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
                  )
                );
              // all correlations have been recorded as expected
              assertThat(eventProcessInstanceDto.getCorrelatedEventsById())
                .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
                .containsValues(
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(firstEventId, secondEventId),
                    MappedEventType.END, ImmutableSet.of(firstEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.END, ImmutableSet.of(secondEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(thirdEventId),
                    MappedEventType.END, ImmutableSet.of(thirdEventId)
                  ))
                );
            }
          );
      });
  }

  @Test
  public void taskWithoutStartMappingGetsAssignedStartDateByPreviousAdjacentEvent_previousAdjacentEventUpdateAlsoUpdatesTaskStartDate() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, endMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createTwoEventAndOneTaskActivitiesProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime updatedFirstEventTime = FIRST_EVENT_DATETIME.minusSeconds(10);
    ingestTestEvent(firstEventId, FIRST_EVENT_NAME, updatedFirstEventTime);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          )
          .satisfies(
            eventProcessInstanceDto -> {
              assertThat(eventProcessInstanceDto.getFlowNodeInstances())
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
                    Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, updatedFirstEventTime, updatedFirstEventTime),
                    Tuple.tuple(secondEventId, USER_TASK_ID_ONE, updatedFirstEventTime, SECOND_EVENT_DATETIME),
                    Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
                  )
                );
              // all correlations have been recorded as expected
              assertThat(eventProcessInstanceDto.getCorrelatedEventsById())
                .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
                .containsValues(
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(firstEventId, secondEventId),
                    MappedEventType.END, ImmutableSet.of(firstEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.END, ImmutableSet.of(secondEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(thirdEventId),
                    MappedEventType.END, ImmutableSet.of(thirdEventId)
                  ))
                );
            }
          );
      });
  }

  @Test
  public void taskWithoutStartMappingGetsAssignedStartDateByPreviousAdjacentEvent_previousAdjacentEventUpdateAlsoUpdatesTaskStartDate_evenIfItResultsInNegativeDuration() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, endMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createTwoEventAndOneTaskActivitiesProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime updatedFirstEventTime = SECOND_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(firstEventId, FIRST_EVENT_NAME, updatedFirstEventTime);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          )
          .satisfies(
            eventProcessInstanceDto -> {
              assertThat(eventProcessInstanceDto.getFlowNodeInstances())
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
                    Tuple.tuple(firstEventId, BPMN_START_EVENT_ID, updatedFirstEventTime, updatedFirstEventTime),
                    Tuple.tuple(secondEventId, USER_TASK_ID_ONE, updatedFirstEventTime, SECOND_EVENT_DATETIME),
                    Tuple.tuple(thirdEventId, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
                  )
                );
              // all correlations have been recorded as expected
              assertThat(eventProcessInstanceDto.getCorrelatedEventsById())
                .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
                .containsValues(
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(firstEventId, secondEventId),
                    MappedEventType.END, ImmutableSet.of(firstEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.END, ImmutableSet.of(secondEventId)
                  )),
                  new EventCorrelationStateDto(ImmutableMap.of(
                    MappedEventType.START, ImmutableSet.of(thirdEventId),
                    MappedEventType.END, ImmutableSet.of(thirdEventId)
                  ))
                );
            }
          );
      });
  }

  @ParameterizedTest(name = "opening gateway type: {0}, closing gateway type: {1}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void taskWithoutStartMappingAfterOpeningExclusiveGateway_previousAdjacentEventUpdateAlsoUpdatesTaskStartDate(
    final String openingGatewayType,
    final String closingGatewayType,
    final String bpmnXml) {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, endMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();
    final OffsetDateTime newFirstEventTimestamp = FIRST_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(firstEventId, FIRST_EVENT_NAME, newFirstEventTimestamp);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto)
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .hasFieldOrPropertyWithValue(
            EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates, Collections.emptyList()
          );
        assertThat(processInstanceDto.getFlowNodeInstances())
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
                firstEventId, BPMN_START_EVENT_ID, newFirstEventTimestamp, newFirstEventTimestamp),
              Tuple.tuple(
                SPLITTING_GATEWAY_ID + "_1", SPLITTING_GATEWAY_ID, newFirstEventTimestamp, newFirstEventTimestamp
              ),
              Tuple.tuple(
                secondEventId, USER_TASK_ID_ONE, newFirstEventTimestamp, SECOND_EVENT_DATETIME
              ),
              Tuple.tuple(
                MERGING_GATEWAY_ID + "_1", MERGING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME
              ),
              Tuple.tuple(
                thirdEventId, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME
              )
            )
          );
        // all correlations have been recorded as expected
        assertThat(processInstanceDto.getCorrelatedEventsById())
          .containsOnlyKeys(firstEventId, secondEventId, thirdEventId)
          .containsValues(
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(firstEventId, secondEventId),
              MappedEventType.END, ImmutableSet.of(firstEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.END, ImmutableSet.of(secondEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(thirdEventId),
              MappedEventType.END, ImmutableSet.of(thirdEventId)
            ))
          );
      });
  }

  @Test
  public void tasksWithoutStartMappingAfterOpeningParallelGateway_previousAdjacentEventUpdateAlsoUpdatesTasksStartDates() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String secondEventId = ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    final String fourthEventId = ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, endMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, endMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createParallelGatewayProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime newFirstEventTimestamp = FIRST_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(firstEventId, FIRST_EVENT_NAME, newFirstEventTimestamp);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getPendingFlowNodeInstanceUpdates())
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .isEmpty();
        assertThat(processInstanceDto.getFlowNodeInstances())
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
                firstEventId, BPMN_START_EVENT_ID, newFirstEventTimestamp, newFirstEventTimestamp),
              Tuple.tuple(
                SPLITTING_GATEWAY_ID + "_1", SPLITTING_GATEWAY_ID, newFirstEventTimestamp, newFirstEventTimestamp
              ),
              Tuple.tuple(
                secondEventId, USER_TASK_ID_ONE, newFirstEventTimestamp, SECOND_EVENT_DATETIME
              ),
              Tuple.tuple(
                thirdEventId, USER_TASK_ID_TWO, newFirstEventTimestamp, THIRD_EVENT_DATETIME
              ),
              Tuple.tuple(
                MERGING_GATEWAY_ID + "_1", MERGING_GATEWAY_ID, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME
              ),
              Tuple.tuple(
                fourthEventId, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME
              )
            )
          );
        // all correlations have been recorded as expected
        assertThat(processInstanceDto.getCorrelatedEventsById())
          .containsOnlyKeys(firstEventId, secondEventId, thirdEventId, fourthEventId)
          .containsValues(
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(firstEventId, secondEventId, thirdEventId),
              MappedEventType.END, ImmutableSet.of(firstEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.END, ImmutableSet.of(secondEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.END, ImmutableSet.of(thirdEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(fourthEventId),
              MappedEventType.END, ImmutableSet.of(fourthEventId)
            ))
          );
      });
  }

  @Test
  public void tasksWithoutStartMappingAfterOpeningParallelGateway_consecutiveGateways_previousAdjacentEventUpdateAlsoUpdatesTaskStartDate() {
    // given
    final String firstEventId = ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    final String thirdEventId = ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, endMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createParallelGatewayProcessDefinitionXml());

    // when
    executeImportCycle();
    final OffsetDateTime newFirstEventTimestamp = FIRST_EVENT_DATETIME.plusSeconds(1);
    ingestTestEvent(firstEventId, FIRST_EVENT_NAME, newFirstEventTimestamp);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getPendingFlowNodeInstanceUpdates())
          // no pending updates should be present as all adjacent updates could get merged with an activity Instance
          .isEmpty();
        assertThat(processInstanceDto.getFlowNodeInstances())
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
                firstEventId, BPMN_START_EVENT_ID, newFirstEventTimestamp, newFirstEventTimestamp),
              Tuple.tuple(
                SPLITTING_GATEWAY_ID + "_1", SPLITTING_GATEWAY_ID, newFirstEventTimestamp, newFirstEventTimestamp
              ),
              Tuple.tuple(
                thirdEventId, USER_TASK_ID_TWO, newFirstEventTimestamp, THIRD_EVENT_DATETIME
              )
            )
          );
        // all correlations have been recorded as expected
        assertThat(processInstanceDto.getCorrelatedEventsById())
          .containsOnlyKeys(firstEventId, thirdEventId)
          .containsValues(
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.START, ImmutableSet.of(firstEventId, thirdEventId),
              MappedEventType.END, ImmutableSet.of(firstEventId)
            )),
            new EventCorrelationStateDto(ImmutableMap.of(
              MappedEventType.END, ImmutableSet.of(thirdEventId)
            ))
          );
      });
  }
}
