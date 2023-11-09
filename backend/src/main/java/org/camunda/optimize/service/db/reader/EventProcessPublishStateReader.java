/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;

import java.util.List;
import java.util.Optional;

public interface EventProcessPublishStateReader {

  Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
    final String eventProcessMappingId);

  List<EventProcessPublishStateDto> getAllEventProcessPublishStates();

  List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(final boolean deleted);

}
