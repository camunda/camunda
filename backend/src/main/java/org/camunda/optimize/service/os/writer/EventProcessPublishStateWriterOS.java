/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.db.writer.EventProcessPublishStateWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessPublishStateWriterOS implements EventProcessPublishStateWriter {

  @Override
  public IdResponseDto createEventProcessPublishState(final EventProcessPublishStateDto eventProcessPublishStateDto) {
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public void updateEventProcessPublishState(final EventProcessPublishStateDto eventProcessPublishStateDto) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public boolean markAsDeletedAllEventProcessPublishStatesForEventProcessMappingId(final String eventProcessMappingId) {
    //todo will be handled in the OPT-7376
    return false;
  }

  @Override
  public void markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(final String eventProcessMappingId,
                                                                                        final String publishStateIdToExclude) {
    //todo will be handled in the OPT-7376
  }

}
