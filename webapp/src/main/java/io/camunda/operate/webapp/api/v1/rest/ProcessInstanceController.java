/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.ProcessInstanceController.URI;

import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("ProcessInstanceControllerV1")
@RequestMapping(URI)
@Tag(name = "ProcessInstance", description = "Process instance API")
@Validated
public class ProcessInstanceController extends ErrorController implements SearchController<ProcessInstance> {

  public static final String URI = "/v1/process-instances";

  @Autowired
  private ProcessInstanceDao processInstanceDao;

  private final QueryValidator<ProcessInstance> queryValidator = new QueryValidator<>();

  @Operation(
      summary = "Search process instances",
      tags = {"process", "instance", "search"},
      responses = {
          @ApiResponse(
              description = "Success",
              responseCode = "200"
          ),
          @ApiResponse(
              description = ServerException.TYPE,
              responseCode = "500",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ClientException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ValidationException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          )
      })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search process instances", content =
    @Content(examples = {
        @ExampleObject(name = "All", value = "{}",description = "Returns all process instances (default return list size is 10)"),
        @ExampleObject(name = "Sorted by field", value = "{  \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}] }", description = "Returns process instances sorted ascending by bpmnProcessId"),
        @ExampleObject(name = "Sorted and paged with size",
            value = "{  \"size\": 3,"
            + "    \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}],"
            + "    \"searchAfter\":["
            + "    \"bigVarProcess\","
            + "    6755399441055870"
            + "  ]}",
            description = "Returns max 3 process instances after 'bigVarProcess' and key 6755399441055870 sorted ascending by bpmnProcessId \n"
                + "To get the next page copy the value of 'sortValues' into 'searchAfter' value.\n"
                + "Sort specification should match the searchAfter specification"),
        @ExampleObject(name = "Filtered and sorted",
            value = "{  \"filter\": {"
                + "      \"processVersion\": 2"
                + "    },"
                + "    \"size\": 50,"
                + "    \"sort\": [{\"field\":\"bpmnProcessId\",\"order\": \"ASC\"}]}",
            description = "Returns max 50 process instances, filtered by processVersion of 2 sorted ascending by bpmnProcessId"),
      }
    )
  )
  @Override
  public Results<ProcessInstance> search(@RequestBody final Query<ProcessInstance> query) {
    logger.debug("search for query {}", query);
    queryValidator.validate(query, ProcessInstance.class);
    return processInstanceDao.search(query);
  }

  @Operation(
      summary = "Get process instance by key",
      tags = {"process","instance"},
      responses = {
          @ApiResponse(
              description = "Success",
              responseCode = "200"
          ),
          @ApiResponse(
              description = ServerException.TYPE,
              responseCode = "500",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ClientException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ResourceNotFoundException.TYPE,
              responseCode = "404",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          )
      })
  @Override
  public ProcessInstance byKey(@Parameter(description = "Key of process instance",required = true) @PathVariable final Long key) {
    return processInstanceDao.byKey(key);
  }

  @Operation(
      summary = "Delete process instance and all dependant data by key",
      tags = {"Process instance","delete","dependant data"},
      responses = {
          @ApiResponse(
              description = "Success",
              responseCode = "200"
          ),
          @ApiResponse(
              description = ServerException.TYPE,
              responseCode = "500",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ClientException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ResourceNotFoundException.TYPE,
              responseCode = "404",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          )
      })
  @ResponseStatus(HttpStatus.OK)
  @DeleteMapping(value = BY_KEY, produces = {MediaType.APPLICATION_JSON_VALUE})
  public ChangeStatus delete(@Parameter(description = "Key of process instance",required = true) @Valid @PathVariable final Long key) {
    return processInstanceDao.delete(key);
  }
}
