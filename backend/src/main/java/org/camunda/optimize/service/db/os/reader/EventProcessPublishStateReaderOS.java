/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.db.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessPublishStateReaderOS implements EventProcessPublishStateReader {

  @Override
  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
      final String eventProcessMappingId) {
    log.debug("Functionality not implemented for OpenSearch");
    return Optional.empty();
  }

  @Override
  public List<EventProcessPublishStateDto> getAllEventProcessPublishStates() {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(
      final boolean deleted) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }
}
