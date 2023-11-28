/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.service.db.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessMappingWriterOS implements EventProcessMappingWriter {

  @Override
  public IdResponseDto createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public void updateEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateRoles(final EventProcessMappingDto eventProcessMappingDto) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public boolean deleteEventProcessMapping(final String eventProcessMappingId) {
    //todo will be handled in the OPT-7376
    return false;
  }

  @Override
  public void deleteEventProcessMappings(final List<String> eventProcessMappingIds) {
    //todo will be handled in the OPT-7376
  }

}
