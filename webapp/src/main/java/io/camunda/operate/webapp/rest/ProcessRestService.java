/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.ProcessRestService.PROCESS_URL;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.ProcessDto;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Processes")
@RestController
@RequestMapping(value = PROCESS_URL)
public class ProcessRestService extends InternalAPIErrorController {

  @Autowired
  protected ProcessReader processReader;

  @Autowired
  protected ProcessInstanceReader processInstanceReader;

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  public static final String PROCESS_URL = "/api/processes";

  @Operation(summary = "Get process BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getProcessDiagram(@PathVariable("id") String processId) {
    return processReader.getDiagram(Long.valueOf(processId));
  }

  @Operation(summary = "Get process by id")
  @GetMapping(path = "/{id}")
  public ProcessDto getProcess(@PathVariable("id") String processId) {
    final ProcessEntity processEntity = processReader.getProcess(Long.valueOf(processId));
    return DtoCreator.create(processEntity, ProcessDto.class);
  }

  @Operation(summary = "List processes grouped by bpmnProcessId")
  @GetMapping(path = "/grouped")
  public List<ProcessGroupDto> getProcessesGrouped() {
    final Map<String, List<ProcessEntity>> processesGrouped = processReader.getProcessesGrouped();
    return ProcessGroupDto.createFrom(processesGrouped, permissionsService);
  }
}
