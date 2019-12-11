/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.util.FileReaderUtil;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
public class EventProcessClient {

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;

  public boolean getIsEventBasedProcessEnabled() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIsEventProcessEnabledRequest()
      .execute(Boolean.class, 200);
  }

  public OptimizeRequestExecutor createCreateEventProcessMappingRequest(final EventProcessMappingDto eventProcessMappingDto) {
    return embeddedOptimizeExtension.getRequestExecutor().buildCreateEventProcessMappingRequest(eventProcessMappingDto);
  }

  public String createEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    return createCreateEventProcessMappingRequest(eventProcessMappingDto).execute(IdDto.class, 200).getId();
  }

  public OptimizeRequestExecutor createGetEventProcessMappingRequest(final String eventProcessMappingId) {
    return embeddedOptimizeExtension.getRequestExecutor().buildGetEventProcessMappingRequest(eventProcessMappingId);
  }

  public EventProcessMappingDto getEventProcessMapping(final String eventProcessMappingId) {
    return createGetEventProcessMappingRequest(eventProcessMappingId).execute(EventProcessMappingDto.class, 200);
  }

  public OptimizeRequestExecutor createGetAllEventProcessMappingsRequest() {
    return embeddedOptimizeExtension.getRequestExecutor().buildGetAllEventProcessMappingsRequests();
  }

  public List<EventProcessMappingDto> getAllEventProcessMappings() {
    return createGetAllEventProcessMappingsRequest()
      .executeAndReturnList(EventProcessMappingDto.class, HttpServletResponse.SC_OK);
  }


  public OptimizeRequestExecutor createUpdateEventProcessMappingRequest(final String eventProcessMappingId,
                                                                        final EventProcessMappingDto eventProcessMappingDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final String eventProcessMappingId, final EventProcessMappingDto updateDto) {
    createUpdateEventProcessMappingRequest(eventProcessMappingId, updateDto).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return embeddedOptimizeExtension.getRequestExecutor().buildPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void publishEventProcessMapping(final String eventProcessMappingId) {
    createPublishEventProcessMappingRequest(eventProcessMappingId).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createCancelPublishEventProcessMappingRequest(final String eventProcessMappingId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildCancelPublishEventProcessMappingRequest(eventProcessMappingId);
  }

  public void cancelPublishEventProcessMapping(final String eventProcessMappingId) {
    createCancelPublishEventProcessMappingRequest(eventProcessMappingId).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public OptimizeRequestExecutor createDeleteEventProcessMappingRequest(final String eventProcessMappingId) {
    return embeddedOptimizeExtension.getRequestExecutor().buildDeleteEventProcessMappingRequest(eventProcessMappingId);
  }

  public void deleteEventProcessMapping(final String eventProcessMappingId) {
    createDeleteEventProcessMappingRequest(eventProcessMappingId).execute(HttpServletResponse.SC_NO_CONTENT);
  }

  public EventProcessMappingDto createEventProcessMappingDto(final String xmlPath) {
    return createEventProcessMappingDto(null, xmlPath);
  }

  public EventProcessMappingDto createEventProcessMappingDto(final String name, final String xmlPath) {
    return createEventProcessMappingDtoWithMappings(null, name, xmlPath);
  }

  @SneakyThrows
  public EventProcessMappingDto createEventProcessMappingDtoWithMappings(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto,
    final String name,
    final String xmlPath) {
    return createEventProcessMappingDtoWithMappingsWithXml(
      flowNodeEventMappingsDto,
      name,
      Optional.ofNullable(xmlPath).map(FileReaderUtil::readFile).orElse(null)
    );
  }

  @SneakyThrows
  public EventProcessMappingDto createEventProcessMappingDtoWithMappingsWithXml(
    final Map<String, EventMappingDto> flowNodeEventMappingsDto, final String name, final String xml) {
    return EventProcessMappingDto.builder()
      .name(Optional.ofNullable(name).orElse(RandomStringUtils.randomAlphanumeric(10)))
      .mappings(flowNodeEventMappingsDto)
      .xml(xml)
      .build();
  }

}
