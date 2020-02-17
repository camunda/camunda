/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.Precision;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.service.es.writer.EventProcessPublishStateWriter;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@AllArgsConstructor
@Slf4j
@Component
public class PublishStateUpdateService {
  private final ExternalEventService eventService;
  private final EventProcessPublishStateWriter eventProcessPublishStateWriter;
  private final EventProcessInstanceImportMediatorManager mediatorManager;
  private final EventProcessInstanceIndexManager eventProcessInstanceIndexManager;

  public void updateEventProcessPublishStates() {
    eventProcessInstanceIndexManager.getPublishedInstanceIndices().values().stream()
      .map(publishStateDto -> Pair.of(
        publishStateDto, mediatorManager.getMediatorByEventProcessPublishId(publishStateDto.getId()).orElse(null))
      )
      .filter(stateAndMediator -> stateAndMediator.getRight() != null)
      .map(stateAndMediator -> {
        final EventProcessPublishStateDto publishState = stateAndMediator.getLeft();
        final long publishTimestamp = publishState.getPublishDateTime().toInstant().toEpochMilli();
        final long lastPersistedTimestamp = stateAndMediator.getRight().getLastPersistedTimestamp();

        if (publishState.getEventImportSources().size() != 1) {
          throw new OptimizeRuntimeException("Cannot update process publish state if there are multiple data sources");
        }
        publishState.getEventImportSources().get(0).setLastImportedEventTimestamp(
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastPersistedTimestamp), ZoneId.systemDefault())
        );

        // for each publishing process we also update progress and eventually state
        if (EventProcessState.PUBLISH_PENDING.equals(publishState.getState())) {
          final double eventCountTillPublishDateTime =
            (double) eventService.countEventsIngestedBeforeAndAtIngestTimestamp(publishTimestamp);
          final double importedEventCount =
            (double) eventService.countEventsIngestedBeforeAndAtIngestTimestamp(lastPersistedTimestamp);
          if (importedEventCount > 0.0D) {
            final double publishProgress = Precision.round(
              Math.min(importedEventCount / eventCountTillPublishDateTime * 100.0D, 100.0D),
              1,
              RoundingMode.DOWN.ordinal()
            );
            publishState.setPublishProgress(publishProgress);
          }
          if (publishState.getPublishProgress() == 100.0D) {
            publishState.setState(EventProcessState.PUBLISHED);
          }
        }

        return publishState;
      })
      .forEach(eventProcessPublishStateWriter::updateEventProcessPublishState);
  }
}
