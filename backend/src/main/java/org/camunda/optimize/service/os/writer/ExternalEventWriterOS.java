/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.db.writer.ExternalEventWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class ExternalEventWriterOS implements ExternalEventWriter {

  @Override
  public void upsertEvents(final List<EventDto> eventDtos) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteEventsOlderThan(final OffsetDateTime timestamp) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteEventsWithIdsIn(final List<String> eventIdsToDelete) {
    //todo will be handled in the OPT-7376
  }

}
