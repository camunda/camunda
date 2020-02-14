/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import org.camunda.optimize.dto.optimize.query.event.EventDto;

import java.util.List;

public interface EventFetcherService {
  List<EventDto> getEventsIngestedAfter(Long eventTimestamp, int limit);

  List<EventDto> getEventsIngestedAt(Long eventTimestamp);
}
