/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import java.util.List;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.es.reader.DetailViewReader;
import org.camunda.operate.rest.dto.detailview.VariableDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;

@Api(tags = {"Workflow instance variables"})
@SwaggerDefinition(tags = {
  @Tag(name = "Workflow instance variables", description = "Workflow instance variables")
})
@RestController
@RequestMapping(value = WORKFLOW_INSTANCE_URL)
public class VariableRestService {

  @Autowired
  private DetailViewReader detailViewReader;

  @ApiOperation("Get variables by workflow instance id and scope id")
  @GetMapping("/{workflowInstanceId}/variables")
  public List<VariableDto> getVariables(@PathVariable String workflowInstanceId, @RequestParam String scopeId) {
    final List<VariableEntity> variableEntities = detailViewReader.getVariables(workflowInstanceId, scopeId);
    return VariableDto.createFrom(variableEntities);
  }

}
