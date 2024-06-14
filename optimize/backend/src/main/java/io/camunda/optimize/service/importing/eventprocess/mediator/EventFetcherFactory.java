/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.eventprocess.mediator;

import io.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import io.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import io.camunda.optimize.service.db.events.EventFetcherService;
import io.camunda.optimize.service.db.reader.CamundaActivityEventReader;
import io.camunda.optimize.service.db.reader.ExternalEventReader;
import io.camunda.optimize.service.db.reader.importindex.TimestampBasedImportIndexReader;
import io.camunda.optimize.service.events.CamundaActivityEventFetcherService;
import io.camunda.optimize.service.events.ExternalEventByGroupsFetcherService;
import io.camunda.optimize.service.events.ExternalEventService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventFetcherFactory {

  private final ExternalEventService externalEventService;
  private final CamundaActivityEventReader camundaActivityEventReader;
  private final ExternalEventReader externalEventReader;
  private final TimestampBasedImportIndexReader timestampBasedImportIndexReader;

  public EventFetcherService<?> createEventFetcherForEventImportSource(
      EventImportSourceDto eventImportSourceDto) {
    if (EventSourceType.EXTERNAL.equals(eventImportSourceDto.getEventImportSourceType())) {
      final boolean includeAllGroups =
          eventImportSourceDto.getEventSourceConfigurations().stream()
              .map(ExternalEventSourceConfigDto.class::cast)
              .anyMatch(ExternalEventSourceConfigDto::isIncludeAllGroups);
      if (includeAllGroups) {
        return externalEventService;
      } else {
        final List<String> groups =
            eventImportSourceDto.getEventSourceConfigurations().stream()
                .map(ExternalEventSourceConfigDto.class::cast)
                .map(ExternalEventSourceConfigDto::getGroup)
                .collect(Collectors.toList());
        return new ExternalEventByGroupsFetcherService(groups, externalEventReader);
      }
    } else if (EventSourceType.CAMUNDA.equals(eventImportSourceDto.getEventImportSourceType())) {
      final CamundaEventSourceConfigDto camundaEventSourceConfig =
          (CamundaEventSourceConfigDto) eventImportSourceDto.getEventSourceConfigurations().get(0);
      return new CamundaActivityEventFetcherService(
          camundaEventSourceConfig, camundaActivityEventReader, timestampBasedImportIndexReader);
    } else {
      throw new OptimizeRuntimeException(
          "Cannot find event fetching service for event import source type: "
              + eventImportSourceDto.getEventImportSourceType());
    }
  }
}
