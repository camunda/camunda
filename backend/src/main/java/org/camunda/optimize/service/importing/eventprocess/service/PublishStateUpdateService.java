/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.camunda.optimize.dto.optimize.query.event.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.service.es.writer.EventProcessPublishStateWriter;
import org.camunda.optimize.service.importing.eventprocess.EventProcessInstanceImportMediatorManager;
import org.camunda.optimize.service.importing.eventprocess.EventProcessInstanceIndexManager;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Duration;

@AllArgsConstructor
@Slf4j
@Component
public class PublishStateUpdateService {

  private final EventProcessPublishStateWriter eventProcessPublishStateWriter;
  private final EventProcessInstanceImportMediatorManager mediatorManager;
  private final EventProcessInstanceIndexManager eventProcessInstanceIndexManager;

  public void updateEventProcessPublishStates() {
    eventProcessInstanceIndexManager.getPublishedInstanceIndices().values().stream()
      .map(publishStateDto -> Pair.of(
        publishStateDto, mediatorManager.getMediatorsByEventProcessPublishId(publishStateDto.getId())
      ))
      .filter(stateAndMediators -> stateAndMediators.getRight() != null)
      .map(stateAndMediator -> {
        final EventProcessPublishStateDto publishState = stateAndMediator.getLeft();

        // for each publishing process we also update progress and eventually state
        if (EventProcessState.PUBLISH_PENDING.equals(publishState.getState())) {
          final double publishProgress = stateAndMediator.getRight()
            .stream()
            .mapToDouble(mediator -> getProgressForImportSource(mediator.getEventImportSourceDto()))
            .average()
            .orElse(0.0D);
          final double roundedPublishProgress = Precision.round(Math.min(publishProgress, 100.0D), 1, RoundingMode.DOWN.ordinal());
          publishState.setPublishProgress(roundedPublishProgress);

          if (publishState.getPublishProgress() == 100.0D) {
            publishState.setState(EventProcessState.PUBLISHED);
          }
        }
        return publishState;
      })
      .forEach(eventProcessPublishStateWriter::updateEventProcessPublishState);
  }

  private Double getProgressForImportSource(EventImportSourceDto eventImportSourceDto) {
    if (eventImportSourceDto.getLastImportedEventTimestamp().toInstant().toEpochMilli() == 0) {
      return 0.0D;
    }

    final double durationBetweenFirstAndLastEventAtTimeOfPublish =
      betweenFirstAndLastEventToImport(eventImportSourceDto);
    final double durationBetweenFirstAndLastImportedEvent =
      betweenFirstAndLastCurrentlyImportedEvent(eventImportSourceDto);

    if (durationBetweenFirstAndLastEventAtTimeOfPublish == 0.0D) {
      return 100.0D;
    } else {
      return Math.min(
        durationBetweenFirstAndLastImportedEvent / durationBetweenFirstAndLastEventAtTimeOfPublish * 100.0D,
        100.0D
      );
    }
  }

  private double betweenFirstAndLastCurrentlyImportedEvent(final EventImportSourceDto eventImportSourceDto) {
    return Duration.between(
      eventImportSourceDto.getFirstEventForSourceAtTimeOfPublishTimestamp().toInstant(),
      eventImportSourceDto.getLastImportedEventTimestamp().toInstant()
    ).toMillis();
  }

  private double betweenFirstAndLastEventToImport(final EventImportSourceDto eventImportSourceDto) {
    return Duration.between(
      eventImportSourceDto.getFirstEventForSourceAtTimeOfPublishTimestamp().toInstant(),
      eventImportSourceDto.getLastEventForSourceAtTimeOfPublishTimestamp().toInstant()
    ).toMillis();
  }

}
