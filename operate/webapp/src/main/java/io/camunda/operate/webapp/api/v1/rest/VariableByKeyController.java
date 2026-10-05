/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.VariableController.URI;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;

import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController("VariableByKeyControllerV1")
@RequestMapping(URI)
@Tag(name = "Variable", description = "Variable API")
@Validated
@ConditionalOnProperty(
    name = "camunda.operate.v1-variable-by-key-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class VariableByKeyController extends ErrorController {

  @Autowired private VariableDao variableDao;
  @Autowired private PermissionsService permissionsService;

  @Operation(
      summary = "Get variable by key",
      security = {@SecurityRequirement(name = "bearer-key"), @SecurityRequirement(name = "cookie")},
      responses = {
        @ApiResponse(description = "Success", responseCode = "200"),
        @ApiResponse(
            description = ServerException.TYPE,
            responseCode = "500",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = ClientException.TYPE,
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = ForbiddenException.TYPE,
            responseCode = "403",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = ResourceNotFoundException.TYPE,
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @ResponseStatus(HttpStatus.OK)
  @GetMapping(
      value = SearchController.BY_KEY,
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public Variable byKey(
      @Valid @Parameter(description = "Key of variable", required = true) @PathVariable
          final Long key) {
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    return variableDao.byKey(key);
  }
}
