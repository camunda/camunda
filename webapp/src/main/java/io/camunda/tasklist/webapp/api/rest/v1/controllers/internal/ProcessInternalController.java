/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.api.rest.v1.controllers.ApiErrorController;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.es.ProcessInstanceWriter;
import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Process", description = "API to manage processes.")
@RestController
@RequestMapping(value = TasklistURIs.PROCESSES_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProcessInternalController extends ApiErrorController {

  @Autowired private ProcessReader processReader;
  @Autowired private ProcessService processService;
  @Autowired private ProcessInstanceWriter processInstanceWriter;

  @Operation(
      summary = "Returns the list of processes by search query",
      description = "Get the processes by `search` query.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true)
      })
  @GetMapping
  public ResponseEntity<List<ProcessResponse>> searchProcesses(
      @RequestParam(defaultValue = StringUtils.EMPTY) String query) {
    final var processes =
        processReader.getProcesses(query).stream()
            .map(ProcessResponse::fromProcessDTO)
            .collect(Collectors.toList());
    return ResponseEntity.ok(processes);
  }

  @Operation(
      summary = "Start process by processDefinitionKey.",
      description = "Start process by `processDefinitionKey`.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the process is not found by `processDefinitionKey`.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PreAuthorize("hasPermission('write')")
  @PatchMapping("{processDefinitionKey}/start")
  public ResponseEntity<ProcessInstanceDTO> startProcessInstance(
      @PathVariable String processDefinitionKey) {
    final var processInstance = processService.startProcessInstance(processDefinitionKey);
    return ResponseEntity.ok(processInstance);
  }

  @Operation(
      summary = "Delete process instance by given processInstanceId.",
      description = "Delete process instance by given `processInstanceId`.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "204",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the `processInstance` with `processInstanceId` is not found`.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description =
                "An error is returned when the `processInstance` with `processInstanceId` could not be deleted`.",
            responseCode = "500",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PreAuthorize("hasPermission('write')")
  @DeleteMapping("{processInstanceId}")
  public ResponseEntity<?> deleteProcessInstance(@PathVariable String processInstanceId) {
    switch (processInstanceWriter.deleteProcessInstance(processInstanceId)) {
      case DELETED:
        return ResponseEntity.noContent().build();
      case NOT_FOUND:
        throw new NotFoundException(
            String.format(
                "The process with processInstanceId: '%s' is not found", processInstanceId));
      default:
        throw new TasklistRuntimeException(
            String.format(
                "The deletion of process with processInstanceId: '%s' could not be deleted",
                processInstanceId));
    }
  }

  @Operation(
      summary = "Return all the public endpoints to start a process by a form.",
      description = "Return all the public endpoints to start a process by a form.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
      })
  @GetMapping("publicEndpoints")
  public ResponseEntity<List<ProcessPublicEndpointsResponse>> getPublicEndpoints() {
    final var publicEndpoints =
        processReader.getProcessesStartedByForm().stream()
            .map(ProcessPublicEndpointsResponse::fromProcessDTO)
            .collect(Collectors.toList());
    return ResponseEntity.ok(publicEndpoints);
  }
}
