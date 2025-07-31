/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController.URI;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_DEFINITION;

import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.Results;
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
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController("ProcessDefinitionControllerV1")
@RequestMapping(URI)
@Tag(name = "ProcessDefinition", description = "Process Definition API")
@Validated
public class ProcessDefinitionController extends ErrorController
    implements SearchController<ProcessDefinition> {

  public static final String URI = "/v1/process-definitions";
  public static final String AS_XML = "/xml";
  private final QueryValidator<ProcessDefinition> queryValidator = new QueryValidator<>();
  @Autowired private ProcessDefinitionDao processDefinitionDao;
  @Autowired private PermissionsService permissionsService;

  @Operation(
      summary = "Search process definitions",
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
      description = "Search examples",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "All",
                    value = "{}",
                    description = "All process instances (default size is 10)"),
                @ExampleObject(
                    name = "Size of returned list",
                    value = "{ \"size\": 5 }",
                    description = "Search process instances and return list of size 5"),
                @ExampleObject(
                    name = "Sort",
                    value = "{ \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}] }",
                    description = "Search process instances and sort by name"),
                @ExampleObject(
                    name = "Sort and size",
                    value = "{ \"size\": 5, \"sort\": [{\"field\":\"name\",\"order\": \"DESC\"}] }",
                    description =
                        "Search process instances, sort descending by name list size of 5"),
                @ExampleObject(
                    name = "Sort and page",
                    value =
                        "{   \"size\": 5,"
                            + "    \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}],"
                            + "    \"searchAfter\": ["
                            + "      \"Called Process\","
                            + "      \"2251799813687281\""
                            + "  ] }",
                    description =
                        "Search process instances,sort by name and page results of size 5. \n "
                            + "To get the next page copy the value of 'sortValues' into 'searchAfter' value.\n"
                            + "Sort specification should match the searchAfter specification"),
                @ExampleObject(
                    name = "Filter and sort ",
                    value =
                        "{   \"filter\": {"
                            + "      \"version\": 1"
                            + "    },"
                            + "    \"size\": 50,"
                            + "    \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}]}",
                    description = "Filter by version and sort by bpmnProcessId"),
              }))
  @Override
  public Results<ProcessDefinition> search(
      @RequestBody(required = false) Query<ProcessDefinition> query) {

    logger.debug("search for query {}", query);
    query = (query == null) ? new Query<>() : query;
    queryValidator.validate(query, ProcessDefinition.class);
    permissionsService.verifyWildcardResourcePermission(
        PROCESS_DEFINITION, READ_PROCESS_DEFINITION);
    return processDefinitionDao.search(query);
  }

  @Operation(
      summary = "Get process definition by key",
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
  public ProcessDefinition byKey(
      @Parameter(description = "Key of process definition", required = true) @Valid @PathVariable
          final Long key) {
    permissionsService.verifyWildcardResourcePermission(
        PROCESS_DEFINITION, READ_PROCESS_DEFINITION);
    return processDefinitionDao.byKey(key);
  }

  @Operation(
      summary = "Get process definition as XML by key",
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
      value = BY_KEY + AS_XML,
      produces = {MediaType.TEXT_XML_VALUE})
  public String xmlByKey(
      @Parameter(description = "Key of process definition", required = true) @Valid @PathVariable
          final Long key) {
    permissionsService.verifyWildcardResourcePermission(
        PROCESS_DEFINITION, READ_PROCESS_DEFINITION);
    return processDefinitionDao.xmlByKey(key);
  }
}
