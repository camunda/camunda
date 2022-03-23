/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.service.DefinitionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeNames;

@AllArgsConstructor
@Path(FlowNodeRestService.FLOW_NODE_PATH)
@Component
@Slf4j
public class FlowNodeRestService {

  public static final String FLOW_NODE_PATH = "/flow-node";
  public static final String FLOW_NODE_NAMES_SUB_PATH = "/flowNodeNames";

  private final DefinitionService definitionService;

  @POST
  @Path(FLOW_NODE_NAMES_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @CacheRequest
  public FlowNodeNamesResponseDto getFlowNodeNames(final FlowNodeIdsToNamesRequestDto request) {
    final FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();

    final Optional<ProcessDefinitionOptimizeDto> processDefinitionXmlDto = definitionService
      .getProcessDefinitionWithXmlAsService(
        DefinitionType.PROCESS,
        request.getProcessDefinitionKey(),
        request.getProcessDefinitionVersion(),
        request.getTenantId()
      );

    if (processDefinitionXmlDto.isPresent()) {
      List<String> nodeIds = request.getNodeIds();
      Map<String, String> flowNodeIdsToNames = extractFlowNodeNames(processDefinitionXmlDto.get().getFlowNodeData());
      if (nodeIds != null && !nodeIds.isEmpty()) {
        for (String id : nodeIds) {
          result.getFlowNodeNames().put(id, flowNodeIdsToNames.get(id));
        }
      } else {
        result.setFlowNodeNames(flowNodeIdsToNames);
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
