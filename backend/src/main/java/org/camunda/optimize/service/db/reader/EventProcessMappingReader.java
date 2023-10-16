/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;

import java.util.List;
import java.util.Optional;

public interface EventProcessMappingReader {

  Optional<EventProcessMappingDto> getEventProcessMapping(final String eventProcessMappingId);

  List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml();

  List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(final String eventProcessMappingId);

}
