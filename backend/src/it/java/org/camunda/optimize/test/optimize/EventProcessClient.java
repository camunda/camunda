/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessRoleRestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingRestDto;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
public class EventProcessClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public boolean getIsEventBasedProcessEnabled() {
    return getRequestExecutor()
      .buildGetIsEventProcessEnabledRequest()
      .execute(Boolean.class, 200);
  }

  public OptimizeRequestExecutor createCreateEventProcessMappingRequest(final EventProcessMappingDto eventProcessMappingDto) {
    return getRequestExecutor().buildCreateEventProcessMappingRequest(eventProcessMappingDto);
  }

  public String createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    return createCreateEventProcessMappingRequest(eventProcessMappingDto).execute(IdDto.class, 200).getId();
  }

  public OptimizeRequestExecutor createGetEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetEventProcessMappingRequest(eventProcessMappingId);
  }

  public EventProcessMappingRestDto getEventProcessMapping(final String eventProcessMappingId) {
    return createGetEventProcessMappingRequest(eventProcessMappingId).execute(EventProcessMappingRestDto.class, 200);
  }

  public OptimizeRequestExecutor createGetAllEventProcessMappingsRequest() {
    return getRequestExecutor().buildGetAllEventProcessMappingsRequests();
  }

  public List<EventProcessMappingDto> getAllEventProcessMappings() {
    return createGetAllEventProcessMappingsRequest()
      .executeAndReturnList(EventProcessMappingDto.class, HttpServletResponse.SC_OK);
  }


  public OptimizeRequestExecutor createUpdateEventProcessMappingRequest(final String eventProcessMappingId,
                                                                        final EventProcessMappingDto eventProcessMappingDto) {
    return getRequestExecutor()
      .buildUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final String eventProcessMappingId, final EventProcessMappingDto updateDto) {
    createUpdateEventProcessMappingRequest(eventProcessMappingId, updateDto).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void publishEventProcessMapping(final String eventProcessMappingId) {
    createPublishEventProcessMappingRequest(eventProcessMappingId).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createCancelPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor()
      .buildCancelPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void cancelPublishEventProcessMapping(final String eventProcessMappingId) {
    createCancelPublishEventProcessMappingRequest(eventProcessMappingId).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createDeleteEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildDeleteEventProcessMappingRequest(eventProcessMappingId);
  }

  public ConflictResponseDto getDeleteConflictsForEventProcessMapping(String eventProcessMappingId) {
    return createGetDeleteConflictsForEventProcessMappingRequest(eventProcessMappingId)
      .execute(ConflictResponseDto.class, HttpServletResponse.SC_OK);
  }

  public OptimizeRequestExecutor createGetDeleteConflictsForEventProcessMappingRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetDeleteConflictsForEventProcessMappingRequest(eventProcessMappingId);
  }

  public void deleteEventProcessMapping(final String eventProcessMappingId) {
    createDeleteEventProcessMappingRequest(eventProcessMappingId).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public EventProcessMappingDto buildEventProcessMappingDto(final String xmlPath) {
    return buildEventProcessMappingDto(null, xmlPath);
  }

  public EventProcessMappingDto buildEventProcessMappingDto(final String name, final String xmlPath) {
    return buildEventProcessMappingDtoWithMappings(null, name, xmlPath);
  }

  public List<EventProcessRoleRestDto> getEventProcessMappingRoles(final String eventProcessMappingId) {
    return createGetEventProcessMappingRolesRequest(eventProcessMappingId)
      .execute(new TypeReference<List<EventProcessRoleRestDto>>() {
      });
  }

  private OptimizeRequestExecutor createGetEventProcessMappingRolesRequest(final String eventProcessMappingId) {
    return getRequestExecutor().buildGetEventProcessMappingRolesRequest(eventProcessMappingId);
  }

  public void updateEventProcessMappingRoles(final String eventProcessMappingId,
                                             final List<EventProcessRoleDto<IdentityDto>> roleRestDtos) {
    createUpdateEventProcessMappingRolesRequest(eventProcessMappingId, roleRestDtos)
      .execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createUpdateEventProcessMappingRolesRequest(final String eventProcessMappingId,
                                                                             final List<EventProcessRoleDto<IdentityDto>> roleRestDtos) {
    return getRequestExecutor().buildUpdateEventProcessRolesRequest(eventProcessMappingId, roleRestDtos);
  }

  @SneakyThrows
  public EventProcessMappingDto buildEventProcessMappingDtoWithMappings(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto,
    final String name,
    final String xmlPath) {
    return buildEventProcessMappingDtoWithMappingsWithXml(flowNodeEventMappingsDto, name, xmlPath);
  }

  @SneakyThrows
  public EventProcessMappingDto buildEventProcessMappingDtoWithMappingsWithXml(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto, final String name, final String xml) {
    return EventProcessMappingDto.builder()
      .name(Optional.ofNullable(name).orElse(RandomStringUtils.randomAlphanumeric(10)))
      .mappings(flowNodeEventMappingsDto)
      .eventSources(new ArrayList<>())
      .xml(xml)
      .build();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
