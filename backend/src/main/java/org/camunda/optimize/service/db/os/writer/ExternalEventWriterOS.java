/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.db.writer.ExternalEventWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class ExternalEventWriterOS implements ExternalEventWriter {

  @Override
  public void upsertEvents(final List<EventDto> eventDtos) {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public void deleteEventsOlderThan(final OffsetDateTime timestamp) {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public void deleteEventsWithIdsIn(final List<String> eventIdsToDelete) {
    log.debug("Functionality not implemented for OpenSearch");
  }
}
