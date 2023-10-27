/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;

import java.time.OffsetDateTime;
import java.util.List;

public interface ExternalEventWriter {

  void upsertEvents(final List<EventDto> eventDtos);

  void deleteEventsOlderThan(final OffsetDateTime timestamp);

  void deleteEventsWithIdsIn(final List<String> eventIdsToDelete);

}
