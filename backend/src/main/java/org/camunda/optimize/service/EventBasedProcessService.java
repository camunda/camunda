/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventBasedProcessDto;
import org.camunda.optimize.service.engine.importing.BpmnModelUtility;
import org.camunda.optimize.service.es.reader.EventBasedProcessReader;
import org.camunda.optimize.service.es.writer.EventBasedProcessWriter;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventBasedProcessService {

  private final EventBasedProcessReader eventBasedProcessReader;
  private final EventBasedProcessWriter eventBasedProcessWriter;

  public IdDto createEventBasedProcess(EventBasedProcessDto eventBasedProcessDto) {
    validateMappingsForProvidedXml(eventBasedProcessDto);
    return eventBasedProcessWriter.createEventBasedProcess(eventBasedProcessDto);
  }

  public void updateEventBasedProcess(EventBasedProcessDto eventBasedProcessDto) {
    validateMappingsForProvidedXml(eventBasedProcessDto);
    eventBasedProcessWriter.updateEventBasedProcess(eventBasedProcessDto);
  }

  public void deleteEventBasedProcess(String eventBasedProcessId) {
    eventBasedProcessWriter.deleteEventBasedProcess(eventBasedProcessId);
  }

  public EventBasedProcessDto getEventBasedProcess(String eventBasedProcessId) {
    return eventBasedProcessReader.getEventBasedProcess(eventBasedProcessId);
  }

  public List<EventBasedProcessDto> getAllEventBasedProcessOmitXml() {
    return eventBasedProcessReader.getAllEventBasedProcessesOmitXml();
  }

  private void validateMappingsForProvidedXml(EventBasedProcessDto eventBasedProcessDto) {
    Set<String> flowNodeIds = eventBasedProcessDto.getXml() == null ? Collections.emptySet() :
      BpmnModelUtility.extractFlowNodeNames(BpmnModelUtility.parseBpmnModel(
      eventBasedProcessDto.getXml())).keySet();
    if (eventBasedProcessDto.getMappings() != null && !flowNodeIds.containsAll(eventBasedProcessDto.getMappings().keySet())) {
      throw new BadRequestException(
        "All Flow Node IDs for event mappings must exist within the provided XML");
    }
  }

}
