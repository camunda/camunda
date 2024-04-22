/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
  private static final CustomQueryValidator<Incident> MESSAGE_SORT_VALIDATOR =
      query -> {
        final List<Sort> sorts = query.getSort();
        if (sorts != null) {
          for (Sort sort : sorts) {
            if (sort.getField().equals(Incident.MESSAGE_FIELD)) {
              throw new ValidationException(
                  String.format("Field '%s' can't be used as sort field", Incident.MESSAGE_FIELD));
            }
          }
        }
      };
  private final QueryValidator<Incident> queryValidator = new QueryValidator<>();
  @Autowired private IncidentDao incidentDao;

  @Operation(
      summary = "Search incidents",
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
      description = "Search incidents",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "All",
                    value = "{}",
                    description = "Returns all incidents (default return list size is 10)."),
                @ExampleObject(
                    name = "Return 20 items",
                    value = "{ \"size\": 20 }",
                    description = "Returns max 20 incidents."),
                @ExampleObject(
                    name = "Sort by field",
                    value = "{ \"sort\": [{\"field\":\"creationTime\",\"order\": \"DESC\"}] }",
                    description = "Returns incidents sorted descending by 'creationTime'"),
                @ExampleObject(
                    name = "Filter by field",
                    value = "{ \"filter\": { \"type\":\"UNHANDLED_ERROR_EVENT\"} }",
                    description =
                        "Returns incidents filtered by 'type'. Field 'message' can't be used for filter and sort"),
                @ExampleObject(
                    name = "Filter and sort",
                    value =
                        "{"
                            + "  \"filter\": {"
                            + "     \"type\":\"CALLED_ELEMENT_ERROR\","
                            + "     \"processDefinitionKey\":\"2251799813686167\""
                            + "  },"
                            + "  \"sort\":[{\"field\":\"creationTime\",\"order\":\"DESC\"}]"
                            + "}",
                    description =
                        "Filter by 'type' and 'processDefinitionKey', sorted descending by 'creationTime'."),
                @ExampleObject(
                    name = "Page by key",
                    value = "{" + " \"searchAfter\":  [" + "    2251799813687785" + "  ]" + "}",
                    description =
                        "Returns paged by using previous returned 'sortValues' value (array)."),
                @ExampleObject(
                    name = "Filter, sort and page",
                    value =
                        "{"
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
                    description =
                        "Returns incidents filtered by 'type' and 'processDefinitionKey', "
                            + "sorted descending by 'creationTime' and paged from previous 'sortValues' value."),
              }))
  @Override
  public Results<Incident> search(@RequestBody(required = false) Query<Incident> query) {
    logger.debug("search for query {}", query);
    query = (query == null) ? new Query<>() : query;
    queryValidator.validate(query, Incident.class, MESSAGE_SORT_VALIDATOR);
    return incidentDao.search(query);
  }

  @Operation(
      summary = "Get incident by key",
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
  @Override
  public Incident byKey(
      @Parameter(description = "Key of incident", required = true) @PathVariable final Long key) {
    return incidentDao.byKey(key);
  }
}
