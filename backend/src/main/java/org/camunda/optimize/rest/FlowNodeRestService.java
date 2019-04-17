/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;

@Path("/flow-node")
@Component
public class FlowNodeRestService {
  private static final Logger logger = LoggerFactory.getLogger(FlowNodeRestService.class);

  private final ProcessDefinitionReader processDefinitionReader;

  @Autowired
  public FlowNodeRestService(final ProcessDefinitionReader processDefinitionReader) {
    this.processDefinitionReader = processDefinitionReader;
  }

  @POST
  @Path("/flowNodeNames")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public FlowNodeNamesResponseDto getFlowNodeNames(final FlowNodeIdsToNamesRequestDto request) {
    final FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionXmlDto = processDefinitionReader
      .getFullyImportedProcessDefinitionAsService(request.getProcessDefinitionKey(), request.getProcessDefinitionVersion());

    if (processDefinitionXmlDto.isPresent()) {
      List<String> nodeIds = request.getNodeIds();
      if (nodeIds != null && !nodeIds.isEmpty()) {
        for (String id : nodeIds) {
          result.getFlowNodeNames().put(id, processDefinitionXmlDto.get().getFlowNodeNames().get(id));
        }
      } else {
        result.setFlowNodeNames(processDefinitionXmlDto.get().getFlowNodeNames());
      }
    } else {
      logger.debug(
        "No process definition found for key {} and version {}, returning empty result.",
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersion()
      );
    }

    return result;
  }
}
