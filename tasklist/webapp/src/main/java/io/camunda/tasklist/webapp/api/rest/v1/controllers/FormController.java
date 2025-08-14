/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.webapp.permission.TasklistPermissionServices.WILDCARD_RESOURCE;

import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Form", description = "API to query forms.")
@RestController
@RequestMapping(value = TasklistURIs.FORMS_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class FormController extends ApiErrorController {

  @Autowired private FormStore formStore;
  @Autowired private TasklistPermissionServices tasklistPermissionServices;

  @Operation(
      summary = "Get a form",
      description =
          "Get the form details by `formId` and `processDefinitionKey` required query param. The `version` query param is optional and is used only for deployed forms (if empty, it retrieves the highest version).",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the form with the `formId` and `processDefinitionKey` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description =
                "A forbidden error is returned when user does not have permission read user tasks.",
            responseCode = "403",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @GetMapping("{formId}")
  public ResponseEntity<FormResponse> getForm(
      @PathVariable @Parameter(description = "The ID of the form.", required = true)
          final String formId,
      @RequestParam
          @Parameter(description = "Reference to the process definition.", required = true)
          final String processDefinitionKey,
      @RequestParam(required = false)
          @Parameter(
              description = "The version of the form. Valid only for deployed forms.",
              required = false)
          final Long version) {
    if (!tasklistPermissionServices.hasPermissionToReadProcessDefinition(WILDCARD_RESOURCE)) {
      throw new ForbiddenActionException(
          "User does not have permission to read resource. Please check your permissions.");
    }

    final var form = formStore.getForm(formId, processDefinitionKey, version);

    // This is to set processDefinitionKey when the form is deployed
    if (form.getProcessDefinitionId() == null) {
      form.setProcessDefinitionId(processDefinitionKey);
    }

    return ResponseEntity.ok(FormResponse.fromFormEntity(form));
  }
}
