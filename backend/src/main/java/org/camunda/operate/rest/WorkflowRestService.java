/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.camunda.operate.rest.WorkflowRestService.WORKFLOW_URL;

import java.util.List;
import java.util.Map;

import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.dto.WorkflowDto;
import org.camunda.operate.rest.dto.WorkflowGroupDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@Api(tags = {"Workflows"})
@SwaggerDefinition(tags = {
  @Tag(name = "Workflows", description = "Workflows")
})
@RestController
@RequestMapping(value = WORKFLOW_URL)
public class WorkflowRestService {

  @Autowired
  protected WorkflowReader workflowReader;

  @Autowired
  protected WorkflowInstanceReader workflowInstanceReader;

  public static final String WORKFLOW_URL = "/api/workflows";

  @ApiOperation("Get workflow BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getWorkflowDiagram(@PathVariable("id") String workflowId) {
    return workflowReader.getDiagram(Long.valueOf(workflowId));
  }

  @ApiOperation("Get workflow by id")
  @GetMapping(path = "/{id}")
  public WorkflowDto getWorkflow(@PathVariable("id") String workflowId) {
    final WorkflowEntity workflowEntity = workflowReader.getWorkflow(Long.valueOf(workflowId));
    return WorkflowDto.createFrom(workflowEntity);
  }

  @ApiOperation("List workflows grouped by bpmnProcessId")
  @GetMapping(path = "/grouped")
  public List<WorkflowGroupDto> getWorkflowsGrouped() {
    final Map<String, List<WorkflowEntity>> workflowsGrouped = workflowReader.getWorkflowsGrouped();
    return WorkflowGroupDto.createFrom(workflowsGrouped);
  }

}
