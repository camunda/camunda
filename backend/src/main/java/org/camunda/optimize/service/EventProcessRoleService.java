/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.service.es.reader.EventProcessMappingReader;
import org.camunda.optimize.service.es.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventProcessRoleService {

  private final EventProcessMappingReader eventProcessMappingReader;
  private final EventProcessMappingWriter eventProcessMappingWriter;
  private final IdentityService identityService;

  public List<EventProcessRoleDto<IdentityDto>> getRoles(final String eventProcessMappingId) {
    return eventProcessMappingReader.getEventProcessRoles(eventProcessMappingId);
  }

  public void updateRoles(final String eventProcessId,
                          final List<EventProcessRoleDto<IdentityDto>> rolesDtoRequest,
                          final String userId) {
    if (rolesDtoRequest.isEmpty()) {
      throw new OptimizeValidationException("Roles are not allowed to be empty!");
    }

    final Set<IdentityDto> invalidIdentities = rolesDtoRequest.stream()
      .map(EventProcessRoleDto::getIdentity)
      .filter(identityDto -> identityDto == null
        || identityDto.getId() == null
        || identityDto.getType() == null
        || !identityService.doesIdentityExists(identityDto)
      )
      .collect(Collectors.toSet());
    if (!invalidIdentities.isEmpty()) {
      throw new OptimizeValidationException(
        String.format("The following provided identities are invalid/do not exist: [%s]!", invalidIdentities)
      );
    }

    final EventProcessMappingDto eventProcessMappingDto = EventProcessMappingDto.builder()
      .id(eventProcessId)
      .roles(rolesDtoRequest)
      .lastModifier(userId)
      .build();
    eventProcessMappingWriter.updateRoles(eventProcessMappingDto);
  }

}
