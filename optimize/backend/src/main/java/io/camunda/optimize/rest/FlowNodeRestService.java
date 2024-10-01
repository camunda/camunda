/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeNames;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.DefinitionService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Path(FlowNodeRestService.FLOW_NODE_PATH)
@Component
public class FlowNodeRestService {

  public static final String FLOW_NODE_PATH = "/flow-node";
  public static final String FLOW_NODE_NAMES_SUB_PATH = "/flowNodeNames";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(FlowNodeRestService.class);

  private final DefinitionService definitionService;

  public FlowNodeRestService(final DefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  @POST
  @Path(FLOW_NODE_NAMES_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @CacheRequest
  public FlowNodeNamesResponseDto getFlowNodeNames(final FlowNodeIdsToNamesRequestDto request) {
    final FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();

    final Optional<ProcessDefinitionOptimizeDto> processDefinitionXmlDto =
        definitionService.getProcessDefinitionWithXmlAsService(
            DefinitionType.PROCESS,
            request.getProcessDefinitionKey(),
            request.getProcessDefinitionVersion(),
            request.getTenantId());

    if (processDefinitionXmlDto.isPresent()) {
      final List<String> nodeIds = request.getNodeIds();
      final Map<String, String> flowNodeIdsToNames =
          extractFlowNodeNames(processDefinitionXmlDto.get().getFlowNodeData());
      if (nodeIds != null && !nodeIds.isEmpty()) {
        for (final String id : nodeIds) {
          result.getFlowNodeNames().put(id, flowNodeIdsToNames.get(id));
        }
      } else {
        result.setFlowNodeNames(flowNodeIdsToNames);
      }
    } else {
      log.debug(
          "No process definition found for key {} and version {}, returning empty result.",
          request.getProcessDefinitionKey(),
          request.getProcessDefinitionVersion());
    }
    return result;
  }
}
