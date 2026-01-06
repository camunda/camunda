/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.DecisionInstanceController.URI;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_DECISION_INSTANCE;

import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController("DecisionInstanceControllerV1")
@RequestMapping(URI)
@Tag(name = "DecisionInstance", description = "Decision Instance API")
@Validated
public class DecisionInstanceController extends ErrorController {

  public static final String URI = "/v1/decision-instances";
  public static final String BY_ID = "/{id}";
  public static final String SEARCH = "/search";

  @Autowired private PermissionsService permissionsService;

  @Autowired private DecisionInstanceDao decisionInstanceDao;

  @Operation(
      summary = "Get decision instance by id",
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
            description = ResourceNotFoundException.TYPE,
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @ResponseStatus(HttpStatus.OK)
  @GetMapping(
      value = BY_ID,
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public DecisionInstance byId(
      @Parameter(description = "Id of decision instance", required = true) @PathVariable
          final String id) {
    permissionsService.verifyWildcardResourcePermission(
        DECISION_DEFINITION, READ_DECISION_INSTANCE);
    return decisionInstanceDao.byId(id);
  }

  @Operation(
      summary = "Search decision instances",
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
                    schema = @Schema(implementation = Error.class)))
      })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Search examples",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "All",
                    value = "{}",
                    description = "All decision instances (default size is 10)"),
                @ExampleObject(
                    name = "Size of returned list",
                    value = "{ \"size\": 5 }",
                    description = "Search decision instances and return list of size 5"),
                @ExampleObject(
                    name = "Sort",
                    value = "{ \"sort\": [{\"field\":\"decisionName\",\"order\": \"ASC\"}] }",
                    description = "Search decision instances and sort ascending by decisionName"),
                @ExampleObject(
                    name = "Sort and size",
                    value =
                        "{ \"size\": 5, \"sort\": [{\"field\":\"decisionName\",\"order\": \"DESC\"}] }",
                    description =
                        "Search decision instances, sort descending by decisionName, and return list of size 5"),
                @ExampleObject(
                    name = "Sort and page",
                    value =
                        "{   \"size\": 5,"
                            + "    \"sort\": [{\"field\":\"decisionName\",\"order\": \"ASC\"}],"
                            + "    \"searchAfter\": ["
                            + "      \"Invoice Classification\","
                            + "      \"2251799813686550\""
                            + "  ] }",
                    description =
                        "Search decision instances, sort ascending by decisionName, and return page of size 5.\n"
                            + "To get the next page, copy the value of 'sortValues' into 'searchAfter' value.\n"
                            + "Sort specification should match the searchAfter specification."),
                @ExampleObject(
                    name = "Filter and sort ",
                    value =
                        "{   \"filter\": {"
                            + "      \"decisionVersion\": 1"
                            + "    },"
                            + "    \"size\": 50,"
                            + "    \"sort\": [{\"field\":\"decisionId\",\"order\": \"ASC\"}]}",
                    description = "Filter by decisionVersion and sort by decisionId"),
              }))
  @ResponseStatus(HttpStatus.OK)
  @PostMapping(
      value = SEARCH,
      consumes = {MediaType.APPLICATION_JSON_VALUE},
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public Results<DecisionInstance> search(
      @RequestBody(required = false) Query<DecisionInstance> query) {
    permissionsService.verifyWildcardResourcePermission(
        DECISION_DEFINITION, READ_DECISION_INSTANCE);
    query = (query == null) ? new Query<>() : query;
    return decisionInstanceDao.search(query);
  }
}
