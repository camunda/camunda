/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.service.es.reader.EventProcessMappingReader;
import org.camunda.optimize.service.es.writer.EventProcessMappingWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.util.configuration.CacheConfiguration;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EventProcessRoleService implements ConfigurationReloadable {

  private final EventProcessMappingWriter eventProcessMappingWriter;
  private final AbstractIdentityService identityService;

  private final LoadingCache<String, List<EventProcessRoleRequestDto<IdentityDto>>> eventProcessRoleReadCache;

  public EventProcessRoleService(final EventProcessMappingReader eventProcessMappingReader,
                                 final EventProcessMappingWriter eventProcessMappingWriter,
                                 final ConfigurationService configurationService,
                                 final AbstractIdentityService identityService) {
    this.eventProcessMappingWriter = eventProcessMappingWriter;
    this.identityService = identityService;

    // this cache serves the purpose to reduce the frequency an actual read is triggered
    // as the event process roles are usually not changing frequently this reduces the latency of calls
    // when multiple authorization checks are done in a short amount of time
    // (mostly listing endpoints for reports and process/decision definitions)
    final CacheConfiguration cacheConfiguration = configurationService.getCaches().getEventProcessRoles();
    this.eventProcessRoleReadCache = Caffeine.newBuilder()
      .maximumSize(cacheConfiguration.getMaxSize())
      .expireAfterWrite(cacheConfiguration.getDefaultTtlMillis(), TimeUnit.MILLISECONDS)
      .build(eventProcessMappingReader::getEventProcessRoles);
  }

  public List<EventProcessRoleRequestDto<IdentityDto>> getRoles(final String eventProcessMappingId) {
    return eventProcessRoleReadCache.get(eventProcessMappingId);
  }

  public void updateRoles(final String eventProcessId,
                          final List<EventProcessRoleRequestDto<IdentityDto>> rolesDtoRequest,
                          final String userId) {
    if (rolesDtoRequest.isEmpty()) {
      throw new OptimizeValidationException("Roles are not allowed to be empty!");
    }

    final Set<IdentityDto> invalidIdentities = rolesDtoRequest.stream()
      .map(EventProcessRoleRequestDto::getIdentity)
      .filter(identityDto -> identityDto == null
        || identityDto.getId() == null
        || identityDto.getType() == null
        || !identityService.doesIdentityExist(identityDto)
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

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    this.eventProcessRoleReadCache.invalidateAll();
  }

}
