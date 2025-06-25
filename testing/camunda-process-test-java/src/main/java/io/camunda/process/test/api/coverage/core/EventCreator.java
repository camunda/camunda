/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api.coverage.core;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.api.coverage.model.Event;
import io.camunda.process.test.api.coverage.model.EventSource;
import io.camunda.process.test.api.coverage.model.EventType;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class EventCreator {

  public static List<Event> createEvents(final CamundaDataSource dataSource) {
    // Process all instances and collect events
    final List<Event> events =
        dataSource.findProcessInstances().stream()
            .flatMap(
                processInstance ->
                    findNodeEventsByProcessInstance(dataSource, processInstance).stream())
            .collect(Collectors.toList());

    // Events to insert for event-based gateways
    final Map<Event, Event> eventsToInsert = new HashMap<>();

    // for event based gateways we need to find out how the flow continues to get the correct
    // sequence flow and add that as an event, as sequence flows after an event based gateway are
    // not reflected in the records
    for (int i = 0; i < events.size(); i++) {
      final Event event = events.get(i);

      if ("EVENT_BASED_GATEWAY".equals(event.getElementType())
          && event.getType() == EventType.END) {

        // If event based gateway is found, get the model and find outgoing flows
        final List<SequenceFlow> outgoingFlows = findSequenceFlowsByEvent(dataSource, event);

        // Check remaining events to find matching start event
        for (int j = i + 1; j < events.size(); j++) {
          final Event nextEvent = events.get(j);

          if (nextEvent.getType() == EventType.START) {
            // Find if this event is a target of any outgoing flow
            final Optional<SequenceFlow> matchingFlow =
                outgoingFlows.stream()
                    .filter(flow -> flow.getTarget().getId().equals(nextEvent.getDefinitionKey()))
                    .findFirst();

            if (matchingFlow.isPresent()) {
              // Create sequence flow event
              final Event flowEvent =
                  new Event(
                      EventSource.SEQUENCE_FLOW,
                      EventType.TAKE,
                      matchingFlow.get().getId(),
                      "sequenceFlow",
                      nextEvent.getModelKey(),
                      nextEvent.getTimestamp());

              eventsToInsert.put(nextEvent, flowEvent);
              break;
            }
          }
        }
      }
    }
    // Add events at correct positions
    for (final Map.Entry<Event, Event> entry : eventsToInsert.entrySet()) {
      events.add(events.indexOf(entry.getKey()), entry.getValue());
    }
    return events;
  }

  private static List<SequenceFlow> findSequenceFlowsByEvent(
      final CamundaDataSource dataSource, final Event event) {
    final CamundaModelProvider modelProvider = new CamundaModelProvider(dataSource);
    final String modelXml = modelProvider.getModel(event.getModelKey()).getXml();
    //    final String modelXml =
    //        dataSource.getProcessDefinitionXmlByProcessDefinitionId(event.getModelKey());
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new ByteArrayInputStream(modelXml.getBytes()));

    return modelInstance.getModelElementsByType(SequenceFlow.class).stream()
        .filter(flow -> flow.getSource().getId().equals(event.getDefinitionKey()))
        .collect(Collectors.toList());
  }

  private static List<Event> findNodeEventsByProcessInstance(
      final CamundaDataSource dataSource, final ProcessInstance processInstance) {

    // Get flow node events
    final List<Event> nodeEvents =
        dataSource
            .findElementInstancesByProcessInstanceKey(processInstance.getProcessInstanceKey())
            .stream()
            .filter(node -> node.getType() != ElementInstanceType.PROCESS)
            .flatMap(node -> mapEvent(node, processInstance.getProcessDefinitionId()).stream())
            .collect(Collectors.toList());

    // Get sequence flow events
    final List<Event> sequenceFlowEvents =
        dataSource
            .findSequenceFlowsByProcessInstanceKey(processInstance.getProcessInstanceKey())
            .stream()
            .map(sequenceFlow -> mapEvent(sequenceFlow, processInstance.getProcessDefinitionId()))
            .collect(Collectors.toList());

    nodeEvents.addAll(sequenceFlowEvents);
    return nodeEvents;
  }

  private static List<Event> mapEvent(
      final ElementInstance flowNodeInstance, final String bpmnProcessId) {
    final EventSource eventSource =
        flowNodeInstance.getType() == ElementInstanceType.SEQUENCE_FLOW
            ? EventSource.SEQUENCE_FLOW
            : EventSource.FLOW_NODE;

    final Set<EventType> eventTypes;

    if (flowNodeInstance.getType() == ElementInstanceType.SEQUENCE_FLOW) {
      eventTypes = Collections.singleton(EventType.TAKE);
    } else if (flowNodeInstance.getState() == ElementInstanceState.COMPLETED
        || flowNodeInstance.getState() == ElementInstanceState.TERMINATED) {
      eventTypes = EnumSet.of(EventType.START, EventType.END);
    } else {
      eventTypes = Collections.singleton(EventType.START);
    }
    return eventTypes.stream()
        .map(
            eventType ->
                new Event(
                    eventSource,
                    eventType,
                    flowNodeInstance.getElementId(),
                    flowNodeInstance.getType().name(),
                    bpmnProcessId,
                    OffsetDateTime.parse(flowNodeInstance.getStartDate())
                        .toInstant()
                        .toEpochMilli()))
        .collect(Collectors.toList());
  }

  private static Event mapEvent(
      final ProcessInstanceSequenceFlow sequenceFlow, final String bpmnProcessId) {
    return new Event(
        EventSource.SEQUENCE_FLOW,
        EventType.TAKE,
        sequenceFlow.getElementId(),
        ElementInstanceType.SEQUENCE_FLOW.name(),
        bpmnProcessId,
        OffsetDateTime.now().toInstant().toEpochMilli());
  }
}
