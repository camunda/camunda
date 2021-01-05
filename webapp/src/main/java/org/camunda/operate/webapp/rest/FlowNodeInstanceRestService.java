/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import java.util.List;
import org.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import org.camunda.operate.webapp.rest.dto.FlowNodeInstanceMetadataDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = {"Flow node instances"})
@SwaggerDefinition(tags = {
    @Tag(name = "Flow node instances", description = "Flow node instances")
})
@RestController
@RequestMapping(value = FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL)
@ConditionalOnProperty(value = "camunda.operate.isNextFlowNodeInstances", havingValue = "true", matchIfMissing = false)
public class FlowNodeInstanceRestService {

  public static final String FLOW_NODE_INSTANCE_URL = "/api/flow-node-instances";

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @ApiOperation("Query flow node instance tree")
  @PostMapping
  public List<FlowNodeInstanceDto> queryFlowNodeInstanceTree(@RequestBody FlowNodeInstanceRequestDto request) {
    if (request == null || request.getWorkflowInstanceId() == null) {
      throw new InvalidRequestException("Workflow instance id must be provided when requesting for activity instance tree.");
    }
    return FlowNodeInstanceDto.createFrom(flowNodeInstanceReader.getFlowNodeInstances(request));
  }

  @ApiOperation("Get metadata by flow node instance id")
  @GetMapping("/{flowNodeInstanceId}/metadata")
  public FlowNodeInstanceMetadataDto queryFlowNodeInstanceMetadata(@PathVariable Long flowNodeInstanceId) {
    return FlowNodeInstanceMetadataDto.createFrom(flowNodeInstanceReader.getFlowNodeInstanceMetadata(String.valueOf(flowNodeInstanceId)));
  }

}
