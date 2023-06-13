/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.camunda.operate.webapp.api.v1.rest.DecisionDefinitionController.URI;

@RestController("DecisionDefinitionControllerV1")
@RequestMapping(URI)
@Tag(name = "DecisionDefinition", description = "Decision definition API")
@Validated
public class DecisionDefinitionController extends ErrorController
    implements SearchController<DecisionDefinition> {

  public static final String URI = "/v1/decision-definitions";

  @Autowired
  private DecisionDefinitionDao decisionDefinitionDao;

  @Override
  public Results<DecisionDefinition> search(Query<DecisionDefinition> query) {
    return null;
  }

  @Operation(summary = "Get decision definition by key", security = { @SecurityRequirement(name = "bearer-key"), @SecurityRequirement(name = "cookie") }, tags = {
      "Decision" }, responses = { @ApiResponse(description = "Success", responseCode = "200"),
      @ApiResponse(description = ServerException.TYPE, responseCode = "500", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ClientException.TYPE, responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ResourceNotFoundException.TYPE, responseCode = "404", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))) })
  @Override
  public DecisionDefinition byKey(Long key) {
    return decisionDefinitionDao.byKey(key);
  }
}
