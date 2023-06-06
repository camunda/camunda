/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.VariableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Variables", description = "API to query variables")
@RestController
@RequestMapping(value = TasklistURIs.VARIABLES_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class VariablesController extends ApiErrorController {

  @Autowired private VariableService variableService;

  @Operation(
      summary = "Get the variable details by variable id.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the variable with the `variableId` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @GetMapping("{variableId}")
  public ResponseEntity<VariableResponse> getVariableById(@PathVariable String variableId) {
    final var variable = variableService.getVariableResponse(variableId);
    return ResponseEntity.ok(variable);
  }
}
