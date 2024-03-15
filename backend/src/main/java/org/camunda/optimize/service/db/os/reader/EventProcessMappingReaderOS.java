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
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.service.db.reader.EventProcessMappingReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessMappingReaderOS implements EventProcessMappingReader {

  @Override
  public Optional<EventProcessMappingDto> getEventProcessMapping(
      final String eventProcessMappingId) {
    log.debug("Functionality not implemented for OpenSearch");
    return Optional.empty();
  }

  @Override
  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(
      final String eventProcessMappingId) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }
}
