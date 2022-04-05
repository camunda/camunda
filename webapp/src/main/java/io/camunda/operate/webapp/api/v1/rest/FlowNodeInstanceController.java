/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;


import static io.camunda.operate.webapp.api.v1.rest.FlowNodeInstanceController.URI;

import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("FlowNodeInstanceControllerV1")
@RequestMapping(URI)
@Tag(name = "Flownode-instance", description = "Flownode Instances API")
@Validated
public class FlowNodeInstanceController extends ErrorController implements SearchController<FlowNodeInstance> {

  public static final String URI = "/v1/flownode-instances";

  @Autowired
  private FlowNodeInstanceDao flowNodeInstanceDao;

  private final QueryValidator<FlowNodeInstance> queryValidator = new QueryValidator<>();

  @Operation(
      summary = "Search flownode-instances",
      tags = {"flownode-instance", "search"},
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search flownode-instances", content =
  @Content(examples = {
      @ExampleObject(name = "All", value = "{}", description = "Returns all flownode instances (default return list size is 10)."),
      @ExampleObject(name = "Return 20 items", value = "{ \"size\": 20 }", description = "Returns max 20 incidents."),
      @ExampleObject(name = "Sort by field", value = "{ \"sort\": [{\"field\":\"endDate\",\"order\": \"DESC\"}] }",
          description = "Returns flownode instances sorted descending by 'endDate'"),
      @ExampleObject(name = "Filter by field", value = "{ \"filter\": { \"incident\": true} }",
          description = "Returns flownode instances filtered by 'incident'."),
      @ExampleObject(name = "Filter and sort", value = "{"
          + "  \"filter\": {"
          + "    \"incident\": true"
          + "  },"
          + "  \"sort\": ["
          + "    {"
          + "      \"field\": \"startDate\","
          + "      \"order\": \"DESC\""
          + "    }"
          + "  ]"
          + "}", description = "Filter by 'incident' , sorted descending by 'startDate'."),
      @ExampleObject(name = "Page by key", value = "{"
          + " \"searchAfter\":  ["
          + "    2251799813687785"
          + "  ]"
          + "}", description = "Returns paged by using previous returned 'sortValues' value (array). Choose an existing key from previous searches to try this."),
      @ExampleObject(name = "Filter, sort and page", value = "{"
          + "  \"filter\": {"
          + "     \"incident\": true"
          + "  },"
          + "  \"sort\":[{\"field\":\"startDate\",\"order\":\"ASC\"}],"
          + "\"searchAfter\":["
          + "    1646904085499,"
          + "    9007199254743288"
          + "  ]"
          + "}",
          description = "Returns flownode instances filtered by 'incident' "
              + ", sorted ascending by 'startDate' and paged from previous 'sortValues' value."),
  }
  )
  )
  @Override
  public Results<FlowNodeInstance> search(@RequestBody final Query<FlowNodeInstance> query) {
    logger.debug("search for query {}", query);
    queryValidator.validate(query, FlowNodeInstance.class);
    return flowNodeInstanceDao.search(query);
  }

  @Operation(
      summary = "Get flownode instance by key",
      tags = {"flownode-instance"},
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
  public FlowNodeInstance byKey(
      @Parameter(description = "Key of flownode instance", required = true) @PathVariable final Long key) {
    return flowNodeInstanceDao.byKey(key);
  }
}

