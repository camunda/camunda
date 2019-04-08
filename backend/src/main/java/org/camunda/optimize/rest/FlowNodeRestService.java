/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/flow-node")
@Component
public class FlowNodeRestService {

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @POST
  @Path("/flowNodeNames")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public FlowNodeNamesResponseDto getFlowNodeNames(
      FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto
  ) {
    return processDefinitionReader.getFlowNodeNames(flowNodeIdsToNamesRequestDto);
  }
}
