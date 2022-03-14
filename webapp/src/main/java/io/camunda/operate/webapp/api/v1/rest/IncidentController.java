/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.IncidentController.URI;

import io.camunda.operate.webapp.api.v1.dao.IncidentDao;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator.CustomQueryValidator;
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
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("IncidentControllerV1")
@RequestMapping(URI)
@Tag(name = "Incident", description = "Incident API")
@Validated
public class IncidentController extends ErrorController implements SearchController<Incident> {

  public static final String URI = "/v1/incidents";

  @Autowired
  private IncidentDao incidentDao;

  private final QueryValidator<Incident> queryValidator = new QueryValidator<>();
  private static final CustomQueryValidator<Incident> messageSortValidator = query -> {
    List<Sort> sorts = query.getSort();
    if (sorts!=null) {
      for(Sort sort: sorts) {
        if(sort.getField().equals(Incident.MESSAGE_FIELD)){
          throw new ValidationException(
              String.format("Field '%s' can't be used as sort field", Incident.MESSAGE_FIELD));
        }
      }
    }
  };

  @Operation(
      summary = "Search incidents",
      tags = {"incidents", "search"},
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search incidents", content =
    @Content(examples = {
        @ExampleObject(name = "All", value = "{}", description = "Returns all incidents (default return list size is 10)."),
        @ExampleObject(name = "Return 20 items", value = "{ \"size\": 20 }", description = "Returns max 20 incidents."),
        @ExampleObject(name = "Sort by field", value = "{ \"sort\": [{\"field\":\"creationTime\",\"order\": \"DESC\"}] }",
            description = "Returns incidents sorted descending by 'creationTime'"),
        @ExampleObject(name = "Filter by field", value = "{ \"filter\": { \"type\":\"UNHANDLED_ERROR_EVENT\"} }",
            description = "Returns incidents filtered by 'type'. Field 'message' can't be used for filter and sort"),
        @ExampleObject(name = "Filter and sort", value = "{"
            + "  \"filter\": {"
            + "     \"type\":\"CALLED_ELEMENT_ERROR\","
            + "     \"processDefinitionKey\":\"2251799813686167\""
            + "  },"
            + "  \"sort\":[{\"field\":\"creationTime\",\"order\":\"DESC\"}]"
            + "}", description = "Filter by 'type' and 'processDefinitionKey', sorted descending by 'creationTime'."),
        @ExampleObject(name = "Page by key", value = "{"
            + " \"searchAfter\":  ["
            + "    2251799813687785"
            + "  ]"
            + "}", description = "Returns paged by using previous returned 'sortValues' value (array)."),
        @ExampleObject(name = "Filter, sort and page", value = "{"
            + "  \"filter\": {"
            + "     \"type\":\"CALLED_ELEMENT_ERROR\","
            + "     \"processDefinitionKey\":\"2251799813686167\""
            + "  },"
            + "  \"sort\":[{\"field\":\"creationTime\",\"order\":\"DESC\"}],"
            + "\"searchAfter\":["
            + "    1646904085499,"
            + "    9007199254743288"
            + "  ]"
            + "}",
            description = "Returns incidents filtered by 'type' and 'processDefinitionKey', "
                + "sorted descending by 'creationTime' and paged from previous 'sortValues' value."),
      }
    )
  )
  @Override
  public Results<Incident> search(@RequestBody final Query<Incident> query) {
    logger.debug("search for query {}", query);
    queryValidator.validate(query, Incident.class, messageSortValidator);
    return incidentDao.search(query);
  }

  @Operation(
      summary = "Get incident by key",
      tags = {"incident"},
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
  public Incident byKey(
      @Parameter(description = "Key of process instance", required = true) @PathVariable final Long key) {
    return incidentDao.byKey(key);
  }
}
