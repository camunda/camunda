/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.ProcessInstanceController.URI;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.DELETE_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;

import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.dao.SequenceFlowDao;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController("ProcessInstanceControllerV1")
@RequestMapping(URI)
@Tag(name = "ProcessInstance", description = "Process Instance API")
@Validated
public class ProcessInstanceController extends ErrorController
    implements SearchController<ProcessInstance> {

  public static final String URI = "/v1/process-instances";
  private final QueryValidator<ProcessInstance> queryValidator = new QueryValidator<>();
  @Autowired private ProcessInstanceDao processInstanceDao;
  @Autowired private SequenceFlowDao sequenceFlowDao;
  @Autowired private FlowNodeStatisticsDao flowNodeStatisticsDao;
  @Autowired private PermissionsService permissionsService;

  @Operation(
      summary = "Search process instances",
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
                    schema = @Schema(implementation = Error.class))),
      })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Search process instances",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "All",
                    value = "{}",
                    description = "Returns all process instances (default return list size is 10)"),
                @ExampleObject(
                    name = "Sorted by field",
                    value = "{  \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}] }",
                    description = "Returns process instances sorted ascending by bpmnProcessId"),
                @ExampleObject(
                    name = "Sorted and paged with size",
                    value =
                        "{  \"size\": 3,"
                            + "    \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}],"
                            + "    \"searchAfter\":["
                            + "    \"bigVarProcess\","
                            + "    6755399441055870"
                            + "  ]}",
                    description =
                        "Returns max 3 process instances after 'bigVarProcess' and key 6755399441055870 sorted ascending by bpmnProcessId \n"
                            + "To get the next page copy the value of 'sortValues' into 'searchAfter' value.\n"
                            + "Sort specification should match the searchAfter specification"),
                @ExampleObject(
                    name = "Filtered and sorted",
                    value =
                        "{  \"filter\": {"
                            + "      \"processVersion\": 2"
                            + "    },"
                            + "    \"size\": 50,"
                            + "    \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}]}",
                    description =
                        "Returns max 50 process instances, filtered by processVersion of 2 sorted ascending by bpmnProcessId"),
              }))
  @Override
  public Results<ProcessInstance> search(
      @RequestBody(required = false) Query<ProcessInstance> query) {

    logger.debug("search for query {}", query);
    query = (query == null) ? new Query<>() : query;
    queryValidator.validate(query, ProcessInstance.class);
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    return processInstanceDao.search(query);
  }

  @Operation(
      summary = "Get process instance by key",
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
  public ProcessInstance byKey(
      @Parameter(description = "Key of process instance", required = true) @PathVariable
          final Long key) {
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    return processInstanceDao.byKey(key);
  }

  @Operation(
      summary = "Delete process instance and all dependant data by key",
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
  @DeleteMapping(
      value = BY_KEY,
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public ChangeStatus delete(
      @Parameter(description = "Key of process instance", required = true) @Valid @PathVariable
          final Long key) {
    permissionsService.verifyWildcardResourcePermission(
        PROCESS_DEFINITION, DELETE_PROCESS_INSTANCE);
    return processInstanceDao.delete(key);
  }

  @Operation(
      summary = "Get sequence flows of process instance by key",
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
  @GetMapping(value = BY_KEY + "/sequence-flows")
  public List<String> sequenceFlowsByKey(
      @Parameter(description = "Key of process instance", required = true) @PathVariable
          final Long key) {
    final Query<SequenceFlow> query =
        new Query<SequenceFlow>()
            .setFilter(new SequenceFlow().setProcessInstanceKey(key))
            .setSize(QueryValidator.MAX_QUERY_SIZE);
    logger.debug("search for query {}", query);
    final QueryValidator<SequenceFlow> queryValidator = new QueryValidator<>();
    queryValidator.validate(query, SequenceFlow.class);
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    checkIfExists(key);
    final Results<SequenceFlow> results = sequenceFlowDao.search(query);
    return results.getItems().stream()
        .map(SequenceFlow::getActivityId)
        .collect(Collectors.toList());
  }

  @Operation(
      summary = "Get flow node statistic by process instance id",
      security = {@SecurityRequirement(name = "bearer-key"), @SecurityRequirement(name = "cookie")},
      responses = {
        @ApiResponse(
            description =
                "Success. Returns statistics for the given process instance, grouped by flow nodes",
            responseCode = "200",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array =
                        @ArraySchema(schema = @Schema(implementation = FlowNodeStatistics.class)))),
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
  @GetMapping(value = BY_KEY + "/statistics")
  public Collection<FlowNodeStatistics> getStatistics(
      @Parameter(description = "Key of process instance", required = true) @PathVariable
          final Long key) {
    permissionsService.verifyWildcardResourcePermission(PROCESS_DEFINITION, READ_PROCESS_INSTANCE);
    checkIfExists(key);
    return flowNodeStatisticsDao.getFlowNodeStatisticsForProcessInstance(key);
  }

  public void checkIfExists(final long key) {
    processInstanceDao.byKey(key);
  }
}
