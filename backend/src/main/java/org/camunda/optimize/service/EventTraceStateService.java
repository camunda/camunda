/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.TracedEventDto;
import org.camunda.optimize.service.es.reader.EventTraceStateReader;
import org.camunda.optimize.service.es.writer.EventSequenceCountWriter;
import org.camunda.optimize.service.es.writer.EventTraceStateWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class EventTraceStateService {

  private final EventTraceStateWriter eventTraceStateWriter;
  private final EventTraceStateReader eventTraceStateReader;

  private final EventSequenceCountWriter eventSequenceCountWriter;

  public void updateTracesAndCountsForEvents(final List<EventDto> eventsToProcess) {
    Map<String, EventTraceStateDto> eventTraceStatesForUpdate = getEventTraceStatesForIds(
      eventsToProcess.stream().map(EventDto::getTraceId).distinct().collect(Collectors.toList())
    ).stream().collect(Collectors.toMap(EventTraceStateDto::getTraceId, Function.identity()));
    Map<String, EventTraceStateDto> eventTraceStatesToCreate = new HashMap<>();
    Map<String, EventSequenceCountDto> sequenceAdjustmentsRequired = new HashMap<>();

    for (EventDto event : eventsToProcess) {
      TracedEventDto tracedEventToAdd = TracedEventDto.fromEventDto(event);
      EventTraceStateDto currentTraceState = eventTraceStatesForUpdate.get(event.getTraceId());
      if (currentTraceState == null) {
        addOrUpdateNewTraceState(eventTraceStatesToCreate, event, tracedEventToAdd);
      } else {
        List<TracedEventDto> eventTrace = currentTraceState.getEventTrace();
        TracedEventDto tracedEventToRemove = getExistingTracedEventToBeReplaced(eventTrace, event);

        // We do nothing if the new event is a duplicate as the trace state will be unaffected
        if (!tracedEventToAdd.equals(tracedEventToRemove)) {
          if (tracedEventToRemove != null) {
            removeExistingEventFromTraceAndRecordAdjustments(
              eventTrace,
              tracedEventToRemove,
              sequenceAdjustmentsRequired
            );
          }
          addEventToTraceAndRecordAdjustments(eventTrace, tracedEventToAdd, sequenceAdjustmentsRequired);
        }
      }
    }

    // we merge the new and updated traces before doing a batch upsert
    eventTraceStatesToCreate.values().stream()
      .peek(traceState -> traceState.getEventTrace().sort(Comparator.comparing(TracedEventDto::getTimestamp)))
      .forEach(eventTrace -> addAdjustmentsForNewTraces(eventTrace, sequenceAdjustmentsRequired));

    eventTraceStatesForUpdate.putAll(eventTraceStatesToCreate);
    upsertEventStateTraces(new ArrayList<>(eventTraceStatesForUpdate.values()));

    // We filter out sequences that have a net 0 effect so don't need to be written
    final List<EventSequenceCountDto> adjustmentsToWrite = sequenceAdjustmentsRequired.keySet().stream()
      .filter(adjustment -> sequenceAdjustmentsRequired.get(adjustment).getCount() != 0L)
      .map(sequenceAdjustmentsRequired::get)
      .collect(Collectors.toList());
    eventSequenceCountWriter.updateEventSequenceCountsWithAdjustments(adjustmentsToWrite);
  }

  public List<EventTraceStateDto> getEventTraceStatesForIds(List<String> traceIds) {
    return eventTraceStateReader.getEventTraceStateForTraceIds(traceIds);
  }

  public void upsertEventStateTraces(final List<EventTraceStateDto> eventTraceStateDtos) {
    eventTraceStateWriter.upsertEventTraceStates(eventTraceStateDtos);
  }

  private void addOrUpdateNewTraceState(final Map<String, EventTraceStateDto> eventTraceStatesToCreate,
                                        final EventDto event, final TracedEventDto tracedEventToAdd) {
    Optional<EventTraceStateDto> existingTraceStateToAdd =
      Optional.ofNullable(eventTraceStatesToCreate.get(event.getTraceId()));

    // we might already have seen a new trace ID in this batch of events - this keeps linked events to a single trace
    if (existingTraceStateToAdd.isPresent()) {
      existingTraceStateToAdd.get().getEventTrace().add(tracedEventToAdd);
    } else {
      EventTraceStateDto newTraceStateDto = EventTraceStateDto.builder()
        .traceId(event.getTraceId())
        .eventTrace(new ArrayList<>(Collections.singletonList(tracedEventToAdd)))
        .build();
      eventTraceStatesToCreate.put(newTraceStateDto.getTraceId(), newTraceStateDto);
    }
  }

  private void addAdjustmentsForNewTraces(final EventTraceStateDto eventTraceStateToCreate,
                                          final Map<String, EventSequenceCountDto> adjustmentsRequired) {
    List<TracedEventDto> tracedEvents = eventTraceStateToCreate.getEventTrace();
    for (TracedEventDto event : tracedEvents) {
      int eventIndex = tracedEvents.indexOf(event);
      if (eventIndex == tracedEvents.size() - 1) {
        incrementSequenceAdjustment(createAdjustment(event, null), adjustmentsRequired);
      } else {
        incrementSequenceAdjustment(createAdjustment(event, tracedEvents.get(eventIndex + 1)), adjustmentsRequired);
      }
    }
  }

  private void addEventToTraceAndRecordAdjustments(final List<TracedEventDto> eventTrace,
                                                   final TracedEventDto tracedEventToAdd,
                                                   final Map<String, EventSequenceCountDto> requiredCountAdjustments) {
    eventTrace.add(tracedEventToAdd);
    eventTrace.sort(Comparator.comparing(TracedEventDto::getTimestamp));
    int indexOfNewEvent = eventTrace.indexOf(tracedEventToAdd);
    TracedEventDto newPreviousEvent = (indexOfNewEvent == 0) ? null : eventTrace.get(indexOfNewEvent - 1);
    TracedEventDto newNextEvent = (indexOfNewEvent == eventTrace.size() - 1) ? null :
      eventTrace.get(indexOfNewEvent + 1);
    if (newPreviousEvent != null) {
      decrementSequenceAdjustment(createAdjustment(newPreviousEvent, newNextEvent), requiredCountAdjustments);
      incrementSequenceAdjustment(createAdjustment(newPreviousEvent, tracedEventToAdd), requiredCountAdjustments);
    }
    incrementSequenceAdjustment(createAdjustment(tracedEventToAdd, newNextEvent), requiredCountAdjustments);
  }

  private void removeExistingEventFromTraceAndRecordAdjustments(final List<TracedEventDto> eventTrace,
                                                                final TracedEventDto tracedEventToRemove,
                                                                final Map<String, EventSequenceCountDto> requiredCountAdjustments) {
    int indexOfCurrentEvent = eventTrace.indexOf(tracedEventToRemove);
    TracedEventDto currentPreviousEvent = (indexOfCurrentEvent == 0) ? null : eventTrace.get(indexOfCurrentEvent - 1);
    TracedEventDto currentNextEvent = (indexOfCurrentEvent == eventTrace.size() - 1) ? null : eventTrace.get(
      indexOfCurrentEvent + 1);
    if (currentPreviousEvent != null) {
      decrementSequenceAdjustment(
        createAdjustment(currentPreviousEvent, tracedEventToRemove),
        requiredCountAdjustments
      );
      incrementSequenceAdjustment(createAdjustment(currentPreviousEvent, currentNextEvent), requiredCountAdjustments);
    }
    decrementSequenceAdjustment(createAdjustment(tracedEventToRemove, currentNextEvent), requiredCountAdjustments);
    eventTrace.remove(tracedEventToRemove);
  }

  private TracedEventDto getExistingTracedEventToBeReplaced(final List<TracedEventDto> eventTrace,
                                                            final EventDto newEventDto) {
    return eventTrace.stream()
      .filter(tracedEvent -> Objects.equals(tracedEvent.getEventId(), newEventDto.getId())
        && Objects.equals(tracedEvent.getGroup(), newEventDto.getGroup())
        && Objects.equals(tracedEvent.getSource(), newEventDto.getSource())
        && Objects.equals(tracedEvent.getEventName(), newEventDto.getEventName()))
      .findAny()
      .orElse(null);
  }

  private void decrementSequenceAdjustment(EventSequenceCountDto adjustment,
                                           Map<String, EventSequenceCountDto> adjustments) {
    addOrUpdateAdjustmentInList(adjustment, adjustments, -1L);
  }

  private void incrementSequenceAdjustment(EventSequenceCountDto adjustment,
                                           Map<String, EventSequenceCountDto> adjustments) {
    addOrUpdateAdjustmentInList(adjustment, adjustments, 1L);
  }

  private void addOrUpdateAdjustmentInList(EventSequenceCountDto sequenceDto,
                                           Map<String, EventSequenceCountDto> adjustments,
                                           Long adjustment) {
    Optional<EventSequenceCountDto> existingAdjustment = Optional.ofNullable(adjustments.get(sequenceDto.getId()));

    if (existingAdjustment.isPresent()) {
      existingAdjustment.get().setCount(existingAdjustment.get().getCount() + adjustment);
    } else {
      sequenceDto.setCount(adjustment);
      adjustments.put(sequenceDto.getId(), sequenceDto);
    }
  }

  private EventSequenceCountDto createAdjustment(TracedEventDto sourceEvent, TracedEventDto targetEvent) {
    EventSequenceCountDto eventSequenceCountDto = EventSequenceCountDto.builder()
      .sourceEvent(Optional.ofNullable(sourceEvent)
                     .map(source -> new EventTypeDto(source.getGroup(), source.getSource(), source.getEventName()))
                     .orElse(null))
      .targetEvent(Optional.ofNullable(targetEvent)
                     .map(target -> new EventTypeDto(target.getGroup(), target.getSource(), target.getEventName()))
                     .orElse(null))
      .build();
    eventSequenceCountDto.generateIdForEventSequenceCountDto();
    return eventSequenceCountDto;
  }

}
