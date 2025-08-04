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
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("VariableControllerV1")
@RequestMapping(URI)
@Tag(name = "Variable", description = "Variable API")
@Validated
public class VariableController extends ErrorController implements SearchController<Variable> {

  public static final String URI = "/v1/variables";
  public static final String BY_PROCESS_INSTANCE_KEY = "/process-instance/{key}";
  private final QueryValidator<Variable> queryValidator = new QueryValidator<>();
  @Autowired private VariableDao variableDao;
  @Autowired private PermissionsService permissionsService;

  @Operation(
      summary = "Search variables for process instances",
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
            description = ValidationException.TYPE,
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
                    schema = @Schema(implementation = Error.class)))
      })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Search variables",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "All",
                    value = "{}",
                    description = "Returns all variables (default return list size is 10)"),
                @ExampleObject(
                    name = "Size",
                    value = "{ \"size\": 20 }",
                    description = "Returns 20 variables "),
                @ExampleObject(
                    name = "Filter and sort",
                    value =
                        "{"
                            + "  \"filter\": {"
                            + "    \"processInstanceKey\": \"9007199254741196\""
                            + "  },"
                            + "  \"sort\": [{\"field\":\"name\",\"order\":\"ASC\"}]"
                            + "}",
                    description =
                        "Returns all variables with 'processInstanceKey' '9007199254741196' sorted ascending by name"),
                @ExampleObject(
                    name = "Paging",
                    value =
                        "{"
                            + "  \"filter\": {"
                            + "    \"processInstanceKey\": \"9007199254741196\""
                            + "  },"
                            + "  \"sort\": [{\"field\":\"name\",\"order\":\"ASC\"}],"
                            + "  \"searchAfter\":["
                            + "    \"small\","
                            + "    9007199254741200"
                            + "  ]"
                            + "}",
                    description =
                        "Returns next variables for 'processInstanceKey' ascending by 'name'. (Copy value of 'sortValues' field of previous results) "),
              }))
  @Override
  public Results<Variable> search(@RequestBody(required = false) Query<Variable> query) {
    logger.debug("search for query {}", query);
    query = (query == null) ? new Query<>() : query;
    queryValidator.validate(query, Variable.class);
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    return variableDao.search(query);
  }

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
  @Override
  public Variable byKey(
      @Parameter(description = "Key of variable", required = true) @PathVariable final Long key) {
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    return variableDao.byKey(key);
  }
}
