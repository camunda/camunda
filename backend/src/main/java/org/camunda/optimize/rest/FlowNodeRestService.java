/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.service.ProcessDefinitionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Path("/flow-node")
@Component
@Slf4j
public class FlowNodeRestService {

  private final ProcessDefinitionService processDefinitionService;

  @POST
  @Path("/flowNodeNames")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @CacheRequest
  public FlowNodeNamesResponseDto getFlowNodeNames(final FlowNodeIdsToNamesRequestDto request) {
    final FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();

    final Optional<ProcessDefinitionOptimizeDto> processDefinitionXmlDto = processDefinitionService
      .getProcessDefinitionXmlAsService(
        request.getProcessDefinitionKey(), request.getProcessDefinitionVersion(), request.getTenantId()
      );

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
      log.debug(
        "No process definition found for key {} and version {}, returning empty result.",
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersion()
      );
    }

    return result;
  }
}
