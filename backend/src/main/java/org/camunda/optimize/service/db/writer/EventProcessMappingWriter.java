/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;

import java.util.List;


public interface EventProcessMappingWriter {

  IdResponseDto createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto);

  void updateEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto);

  void updateRoles(final EventProcessMappingDto eventProcessMappingDto);

  boolean deleteEventProcessMapping(final String eventProcessMappingId);

  void deleteEventProcessMappings(final List<String> eventProcessMappingIds);

}
