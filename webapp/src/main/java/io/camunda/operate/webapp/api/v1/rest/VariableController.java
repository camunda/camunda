/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.VariableController.URI;

import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
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

  @Autowired
  private VariableDao variableDao;

  private final QueryValidator<Variable> queryValidator = new QueryValidator<>();

  @Operation(
      summary = "Search variables for process instances",
      tags = {"process", "variable", "search"},
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search variables", content =
   @Content(examples = {
        @ExampleObject(name = "All", value = "{}",description = "Returns all variables (default return list size is 10)"),
        @ExampleObject(name = "Size", value = "{ \"size\": 20 }",description = "Returns 20 variables "),
        @ExampleObject(name = "Filter and sort", value = "{"
            + "  \"filter\": {"
            + "    \"processInstanceKey\": \"9007199254741196\""
            + "  },"
            + "  \"sort\": [{\"field\":\"name\",\"order\":\"ASC\"}]"
            + "}",description = "Returns all variables with 'processInstanceKey' '9007199254741196' sorted ascending by name"),
        @ExampleObject(name = "Paging", value = "{"
            + "  \"filter\": {"
            + "    \"processInstanceKey\": \"9007199254741196\""
            + "  },"
            + "  \"sort\": [{\"field\":\"name\",\"order\":\"ASC\"}],"
            + "  \"searchAfter\":["
            + "    \"small\","
            + "    9007199254741200"
            + "  ]"
            + "}",
           description = "Returns next variables for 'processInstanceKey' ascending by 'name'. (Copy value of 'sortValues' field of previous results) "),
      }
    )
  )
  @Override
  public Results<Variable> search(@RequestBody final Query<Variable> query) {
    logger.debug("search for query {}", query);
    queryValidator.validate(query, Variable.class);
    return variableDao.search(query);
  }

  @Operation(
      summary = "Get variable by key",
      tags = {"variable"},
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
  public Variable byKey(@Parameter(description = "Key of variable",required = true) @PathVariable final Long key) {
    return variableDao.byKey(key);
  }
}
