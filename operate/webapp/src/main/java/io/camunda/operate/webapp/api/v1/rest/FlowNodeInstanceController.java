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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@Tag(name = "FlownodeInstance", description = "Flownode Instance API")
@Validated
public class FlowNodeInstanceController extends ErrorController
    implements SearchController<FlowNodeInstance> {

  public static final String URI = "/v1/flownode-instances";
  private final QueryValidator<FlowNodeInstance> queryValidator = new QueryValidator<>();
  @Autowired private FlowNodeInstanceDao flowNodeInstanceDao;

  @Operation(
      summary = "Search flownode-instances",
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
      description = "Search flownode-instances",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "All",
                    value = "{}",
                    description =
                        "Returns all flownode instances (default return list size is 10)."),
                @ExampleObject(
                    name = "Return 20 items",
                    value = "{ \"size\": 20 }",
                    description = "Returns max 20 incidents."),
                @ExampleObject(
                    name = "Sort by field",
                    value = "{ \"sort\": [{\"field\":\"endDate\",\"order\": \"DESC\"}] }",
                    description = "Returns flownode instances sorted descending by 'endDate'"),
                @ExampleObject(
                    name = "Filter by field",
                    value = "{ \"filter\": { \"incident\": true} }",
                    description = "Returns flownode instances filtered by 'incident'."),
                @ExampleObject(
                    name = "Filter and sort",
                    value =
                        "{"
                            + "  \"filter\": {"
                            + "    \"incident\": true"
                            + "  },"
                            + "  \"sort\": ["
                            + "    {"
                            + "      \"field\": \"startDate\","
                            + "      \"order\": \"DESC\""
                            + "    }"
                            + "  ]"
                            + "}",
                    description = "Filter by 'incident' , sorted descending by 'startDate'."),
                @ExampleObject(
                    name = "Page by key",
                    value = "{" + " \"searchAfter\":  [" + "    2251799813687785" + "  ]" + "}",
                    description =
                        "Returns paged by using previous returned 'sortValues' value (array). Choose an existing key from previous searches to try this."),
                @ExampleObject(
                    name = "Filter, sort and page",
                    value =
                        "{"
                            + "  \"filter\": {"
                            + "     \"incident\": true"
                            + "  },"
                            + "  \"sort\":[{\"field\":\"startDate\",\"order\":\"ASC\"}],"
                            + "\"searchAfter\":["
                            + "    1646904085499,"
                            + "    9007199254743288"
                            + "  ]"
                            + "}",
                    description =
                        "Returns flownode instances filtered by 'incident' "
                            + ", sorted ascending by 'startDate' and paged from previous 'sortValues' value."),
              }))
  @Override
  public Results<FlowNodeInstance> search(
      @RequestBody(required = false) Query<FlowNodeInstance> query) {
    logger.debug("search for query {}", query);
    query = (query == null) ? new Query<>() : query;
    queryValidator.validate(query, FlowNodeInstance.class);
    return flowNodeInstanceDao.search(query);
  }

  @Operation(
      summary = "Get flow node instance by key",
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
  public FlowNodeInstance byKey(
      @Parameter(description = "Key of flownode instance", required = true) @PathVariable
          final Long key) {
    return flowNodeInstanceDao.byKey(key);
  }
}
