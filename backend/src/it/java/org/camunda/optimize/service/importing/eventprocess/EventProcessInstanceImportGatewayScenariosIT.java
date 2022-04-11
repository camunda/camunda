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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessInstanceImportGatewayScenariosIT extends AbstractEventProcessIT {

  @ParameterizedTest(name = "opening gateway type: {0}, closing gateway type {1}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void gatewaysAreGeneratedWhenSurroundingEventsHaveOccurred(String openingGatewayType,
                                                                    String closingGatewayType,
                                                                    String bpmnXml) {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, THIRD_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(
              openingGatewayType,
              SPLITTING_GATEWAY_ID,
              FIRST_EVENT_DATETIME,
              isEventBasedGatewayType(openingGatewayType) ? SECOND_EVENT_DATETIME : FIRST_EVENT_DATETIME
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(closingGatewayType, MERGING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void parallelAreGeneratedWhenSurroundingEventsHaveOccurred() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createParallelGatewayProcessDefinitionXml());

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(PARALLEL_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_TWO, THIRD_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(PARALLEL_GATEWAY_TYPE, MERGING_GATEWAY_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void eventBasedGatewaysAreGeneratedWithCorrectDurationWhenSourceEventEndDateIsNotEqualToTargetEventStartDate() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createEventBasedGatewayProcessDefinitionXml());

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(EVENT_BASED_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FIRST_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, THIRD_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void closingParallelGatewaysAreGeneratedWithCorrectDurationWhenFirstSourceEventEndDateIsNotEqualToTargetEventStartDate() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);
    ingestTestEvent(FIFTH_EVENT_NAME, FIFTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startAndEndMapping(SECOND_EVENT_NAME, THIRD_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(FOURTH_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FIFTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createParallelGatewayProcessDefinitionXml());

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FIFTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(PARALLEL_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_TWO, FOURTH_EVENT_DATETIME, FIFTH_EVENT_DATETIME),
            Tuple.tuple(PARALLEL_GATEWAY_TYPE, MERGING_GATEWAY_ID, THIRD_EVENT_DATETIME, FIFTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FIFTH_EVENT_DATETIME, FIFTH_EVENT_DATETIME)
          )
        );
      });
  }

  @ParameterizedTest(name = "opening gateway type: {0}, closing gateway type {1}")
  @MethodSource("allSupportedGatewayXmlVariations")
  public void gatewayIsNotGeneratedIfPreviousEventHasNotYetEnded(String openingGatewayType,
                                                                 String closingGatewayType,
                                                                 String bpmnXml) {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startAndEndMapping(SECOND_EVENT_NAME, THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertActiveProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(
              openingGatewayType,
              SPLITTING_GATEWAY_ID,
              FIRST_EVENT_DATETIME,
              isEventBasedGatewayType(openingGatewayType) ? SECOND_EVENT_DATETIME : FIRST_EVENT_DATETIME
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, null)
          )
        );
      });
  }

  @Test
  public void exclusiveGatewaysAreGeneratedForModelsContainingLoop() {
    // given
    OffsetDateTime secondEventSecondOccurrenceTime = THIRD_EVENT_DATETIME.plusSeconds(1);
    OffsetDateTime thirdEventSecondOccurrenceTime = secondEventSecondOccurrenceTime.plusSeconds(1);
    OffsetDateTime secondEventThirdOccurrenceTime = thirdEventSecondOccurrenceTime.plusSeconds(1);

    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, secondEventSecondOccurrenceTime);
    ingestTestEvent(THIRD_EVENT_NAME, thirdEventSecondOccurrenceTime);
    ingestTestEvent(SECOND_EVENT_NAME, secondEventThirdOccurrenceTime);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createExclusiveGatewayProcessDefinitionWithLoopXml());

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID, SECOND_EVENT_DATETIME, SECOND_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_TWO, THIRD_EVENT_DATETIME, secondEventSecondOccurrenceTime),
            Tuple.tuple(
              EXCLUSIVE_GATEWAY_TYPE,
              MERGING_GATEWAY_ID,
              secondEventSecondOccurrenceTime,
              secondEventSecondOccurrenceTime
            ),
            Tuple.tuple(
              USER_TASK_TYPE,
              USER_TASK_ID_ONE,
              secondEventSecondOccurrenceTime,
              thirdEventSecondOccurrenceTime
            ),
            Tuple.tuple(
              EXCLUSIVE_GATEWAY_TYPE,
              SPLITTING_GATEWAY_ID,
              thirdEventSecondOccurrenceTime,
              thirdEventSecondOccurrenceTime
            ),
            Tuple.tuple(
              USER_TASK_TYPE,
              USER_TASK_ID_TWO,
              thirdEventSecondOccurrenceTime,
              secondEventThirdOccurrenceTime
            ),
            Tuple.tuple(
              EXCLUSIVE_GATEWAY_TYPE,
              MERGING_GATEWAY_ID,
              secondEventThirdOccurrenceTime,
              secondEventThirdOccurrenceTime
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, secondEventThirdOccurrenceTime, FOURTH_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void exclusiveGatewaysAreGeneratedWithUniqueIdsEvenIfMultipleOccurrences() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, FOURTH_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, FIFTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));

    createAndPublishEventProcessMapping(
      eventMappings,
      createExclusiveGatewayProcessDefinitionWithEventBeforeGatewayXml()
    );

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertActiveProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, SECOND_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, SECOND_EVENT_DATETIME, SECOND_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, FIFTH_EVENT_DATETIME, FIFTH_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FIFTH_EVENT_DATETIME, FIFTH_EVENT_DATETIME)
          )
        );
        final Set<String> gatewayEventIds = processInstanceDto.getFlowNodeInstances().stream()
          .filter(event -> event.getFlowNodeType().equals(EXCLUSIVE_GATEWAY_TYPE))
          .map(FlowNodeInstanceDto::getFlowNodeInstanceId)
          .collect(Collectors.toSet());
        assertThat(gatewayEventIds)
          .hasSize(4)
          .containsExactlyInAnyOrder(
            SPLITTING_GATEWAY_ID + "_1",
            SPLITTING_GATEWAY_ID + "_2",
            SPLITTING_GATEWAY_ID + "_3",
            SPLITTING_GATEWAY_ID + "_4"
          );
      });
  }

  @ParameterizedTest(name = "opening gateway type: {0}, closing gateway type {1}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void gatewaysAreGeneratedCorrectlyWhenEventsAreIngestedAcrossMultipleBatches(String openingGatewayType,
                                                                                      String closingGatewayType,
                                                                                      String bpmnXml) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();
    executeImportCycle();
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, THIRD_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(
              openingGatewayType,
              SPLITTING_GATEWAY_ID,
              FIRST_EVENT_DATETIME,
              isEventBasedGatewayType(openingGatewayType) ? SECOND_EVENT_DATETIME : FIRST_EVENT_DATETIME
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(closingGatewayType, MERGING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
          )
        );
      });
  }

  @ParameterizedTest(name = "opening gateway type: {0}, closing gateway type {1}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void gatewaysAreGeneratedCorrectlyWhenOutOfOrderEventsAreIngestedAcrossMultipleBatches(String openingGatewayType,
                                                                                                String closingGatewayType,
                                                                                                String bpmnXml) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventImportConfiguration().setMaxPageSize(1);
    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(THIRD_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    executeImportCycle();
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    executeImportCycle();
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, THIRD_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(
              openingGatewayType,
              SPLITTING_GATEWAY_ID,
              FIRST_EVENT_DATETIME,
              isEventBasedGatewayType(openingGatewayType) ? SECOND_EVENT_DATETIME : FIRST_EVENT_DATETIME
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(closingGatewayType, MERGING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void exclusiveGatewaysAreGeneratedWhenTwoConsecutiveGatewaysExistOnPath() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(
      eventMappings,
      createExclusiveGatewayProcessDefinitionWithConsecutiveGatewaysXml()
    );

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID_TWO, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_TWO, THIRD_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID_TWO, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME)
          )
        );
      });
  }

  @ParameterizedTest(name = "opening gateway type: {0}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void openingGatewaysAreNotGeneratedWhenPreviousEventIsUnmapped(String openingGatewayType,
                                                                        String closingGatewayType,
                                                                        String bpmnXml) {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(USER_TASK_ID_ONE, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(SECOND_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, null, SECOND_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, FIRST_EVENT_DATETIME, SECOND_EVENT_DATETIME),
            Tuple.tuple(closingGatewayType, MERGING_GATEWAY_ID, SECOND_EVENT_DATETIME, SECOND_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, SECOND_EVENT_DATETIME, SECOND_EVENT_DATETIME)
          )
        );
      });
  }

  @ParameterizedTest(name = "closing gateway type {1}")
  @MethodSource("exclusiveAndEventBasedGatewayXmlVariations")
  public void closingGatewaysAreNotGeneratedWhenNextEventIsUnmapped(String openingGatewayType,
                                                                    String closingGatewayType,
                                                                    String bpmnXml) {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, bpmnXml);

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertActiveProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(
              openingGatewayType,
              SPLITTING_GATEWAY_ID,
              FIRST_EVENT_DATETIME,
              isEventBasedGatewayType(openingGatewayType) ? SECOND_EVENT_DATETIME : FIRST_EVENT_DATETIME
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, SECOND_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void gatewaysEmbeddedBetweenGeneratedGatewaysAreNotCurrentlyGeneratedInLoopingModels() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_FOUR, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(
      eventMappings,
      createExclusiveGatewayProcessDefinitionWithThreeConsecutiveGatewaysAndLoopXml()
    );

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID_FOUR, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(
              EXCLUSIVE_GATEWAY_TYPE,
              SPLITTING_GATEWAY_ID_THREE,
              SECOND_EVENT_DATETIME,
              SECOND_EVENT_DATETIME
            ),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID_THREE, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, SPLITTING_GATEWAY_ID, THIRD_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_FOUR, THIRD_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(EXCLUSIVE_GATEWAY_TYPE, MERGING_GATEWAY_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(
              EXCLUSIVE_GATEWAY_TYPE,
              SPLITTING_GATEWAY_ID_FOUR,
              FOURTH_EVENT_DATETIME,
              FOURTH_EVENT_DATETIME
            ),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void inclusiveGatewaysAreNotGenerated() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FOURTH_EVENT_NAME));

    createAndPublishEventProcessMapping(eventMappings, createInclusiveGatewayProcessDefinitionXml());

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FOURTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_TWO, THIRD_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FOURTH_EVENT_DATETIME, FOURTH_EVENT_DATETIME)
          )
        );
      });
  }

  @Test
  public void mixedDirectionGatewaysAreNotGenerated() {
    // given
    ingestTestEvent(FIRST_EVENT_NAME, FIRST_EVENT_DATETIME);
    ingestTestEvent(SECOND_EVENT_NAME, SECOND_EVENT_DATETIME);
    ingestTestEvent(THIRD_EVENT_NAME, THIRD_EVENT_DATETIME);
    ingestTestEvent(FOURTH_EVENT_NAME, FOURTH_EVENT_DATETIME);
    ingestTestEvent(FIFTH_EVENT_NAME, FIFTH_EVENT_DATETIME);

    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startMapping(FIRST_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_ONE, startMapping(SECOND_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_TWO, startMapping(THIRD_EVENT_NAME));
    eventMappings.put(USER_TASK_ID_THREE, startMapping(FOURTH_EVENT_NAME));
    eventMappings.put(BPMN_END_EVENT_ID, startMapping(FIFTH_EVENT_NAME));

    createAndPublishEventProcessMapping(
      eventMappings,
      createExclusiveGatewayProcessDefinitionWithMixedDirectionGatewaysXml()
    );

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertCompletedProcessInstance(processInstanceDto, FIRST_EVENT_DATETIME, FIFTH_EVENT_DATETIME);
        assertFlowNodeEventsForProcessInstance(
          processInstanceDto,
          Arrays.asList(
            Tuple.tuple(START_EVENT_TYPE, BPMN_START_EVENT_ID, FIRST_EVENT_DATETIME, FIRST_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_ONE, SECOND_EVENT_DATETIME, THIRD_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_TWO, THIRD_EVENT_DATETIME, FOURTH_EVENT_DATETIME),
            Tuple.tuple(USER_TASK_TYPE, USER_TASK_ID_THREE, FOURTH_EVENT_DATETIME, FIFTH_EVENT_DATETIME),
            Tuple.tuple(END_EVENT_TYPE, BPMN_END_EVENT_ID, FIFTH_EVENT_DATETIME, FIFTH_EVENT_DATETIME)
          )
        );
      });
  }

  private static Stream<Arguments> allSupportedGatewayXmlVariations() {
    return Stream.concat(
      exclusiveAndEventBasedGatewayXmlVariations(),
      Stream.of(Arguments.of(PARALLEL_GATEWAY_TYPE, PARALLEL_GATEWAY_TYPE, createParallelGatewayProcessDefinitionXml()))
    );
  }

  private void assertActiveProcessInstance(final ProcessInstanceDto processInstance, final OffsetDateTime startDate) {
    assertThat(processInstance)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.state, PROCESS_INSTANCE_STATE_ACTIVE)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.duration, null)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.startDate, startDate)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.endDate, null);
  }

  private void assertCompletedProcessInstance(final ProcessInstanceDto processInstance, final OffsetDateTime startDate,
                                              final OffsetDateTime endDate) {
    assertThat(processInstance)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.state, PROCESS_INSTANCE_STATE_COMPLETED)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.startDate, startDate)
      .hasFieldOrPropertyWithValue(ProcessInstanceDto.Fields.endDate, endDate)
      .satisfies(processInstanceDto -> {
        if (processInstanceDto.getStartDate() != null) {
          assertThat(processInstanceDto.getDuration()).isEqualTo(Duration.between(startDate, endDate).toMillis());
        } else {
          assertThat(processInstance.getDuration()).isNull();
        }
      });
  }

  private void assertFlowNodeEventsForProcessInstance(ProcessInstanceDto processInstanceDto,
                                                      List<Tuple> flowNodeEvents) {
    assertThat(processInstanceDto)
      .extracting(ProcessInstanceDto::getFlowNodeInstances)
      .satisfies(events -> assertThat(events)
        .allSatisfy(simpleEventDto -> {
          if (simpleEventDto.getEndDate() == null) {
            assertThat(simpleEventDto.getTotalDurationInMs()).isNull();
          } else {
            assertThat(simpleEventDto).hasNoNullFieldsOrPropertiesExcept(NULLABLE_FLOW_NODE_FIELDS_TO_IGNORE);
          }
          String activityType = simpleEventDto.getFlowNodeType();
          if (activityType.equals(EXCLUSIVE_GATEWAY_TYPE) || activityType.equals(PARALLEL_GATEWAY_TYPE)
            || activityType.equals(EVENT_BASED_GATEWAY_TYPE)) {
            assertThat(simpleEventDto.getFlowNodeInstanceId()).startsWith(simpleEventDto.getFlowNodeId());
          }
        })
        .extracting(
          FlowNodeInstanceDto::getFlowNodeType,
          FlowNodeInstanceDto::getFlowNodeId,
          FlowNodeInstanceDto::getStartDate,
          FlowNodeInstanceDto::getEndDate
        )
        .containsExactlyInAnyOrderElementsOf(flowNodeEvents)
      );
  }

  private boolean isEventBasedGatewayType(String gatewayType) {
    return gatewayType.equalsIgnoreCase(EVENT_BASED_GATEWAY_TYPE);
  }

}
