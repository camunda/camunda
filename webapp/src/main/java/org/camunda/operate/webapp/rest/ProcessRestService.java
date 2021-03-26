/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import static org.camunda.operate.webapp.rest.ProcessRestService.PROCESS_URL;

import java.util.List;
import java.util.Map;

import org.camunda.operate.entities.ProcessEntity;
import org.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import org.camunda.operate.webapp.es.reader.ProcessReader;
import org.camunda.operate.webapp.rest.dto.ProcessDto;
import org.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@Api(tags = {"Processes"})
@SwaggerDefinition(tags = {
  @Tag(name = "Processes", description = "Processes")
})
@RestController
@RequestMapping(value = PROCESS_URL)
public class ProcessRestService {

  @Autowired
  protected ProcessReader processReader;

  @Autowired
  protected ProcessInstanceReader processInstanceReader;

  public static final String PROCESS_URL = "/api/processes";

  @ApiOperation("Get process BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getProcessDiagram(@PathVariable("id") String processId) {
    return processReader.getDiagram(Long.valueOf(processId));
  }

  @ApiOperation("Get process by id")
  @GetMapping(path = "/{id}")
  public ProcessDto getProcess(@PathVariable("id") String processId) {
    final ProcessEntity processEntity = processReader.getProcess(Long.valueOf(processId));
    return ProcessDto.createFrom(processEntity);
  }

  @ApiOperation("List processes grouped by bpmnProcessId")
  @GetMapping(path = "/grouped")
  public List<ProcessGroupDto> getProcessesGrouped() {
    final Map<String, List<ProcessEntity>> processesGrouped = processReader.getProcessesGrouped();
    return ProcessGroupDto.createFrom(processesGrouped);
  }

}
