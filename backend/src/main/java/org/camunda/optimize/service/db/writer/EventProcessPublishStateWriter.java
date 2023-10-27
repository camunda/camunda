/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;

public interface EventProcessPublishStateWriter {

  IdResponseDto createEventProcessPublishState(final EventProcessPublishStateDto eventProcessPublishStateDto);

  void updateEventProcessPublishState(final EventProcessPublishStateDto eventProcessPublishStateDto);

  boolean markAsDeletedAllEventProcessPublishStatesForEventProcessMappingId(final String eventProcessMappingId);

  void markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(
    final String eventProcessMappingId,
    final String publishStateIdToExclude);

}
