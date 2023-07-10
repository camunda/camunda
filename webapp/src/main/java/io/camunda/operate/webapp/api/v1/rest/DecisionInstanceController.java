/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Error;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static io.camunda.operate.webapp.api.v1.rest.DecisionInstanceController.URI;

@RestController("DecisionInstanceControllerV1")
@RequestMapping(URI)
@Tag(name = "DecisionInstance", description = "Decision instance API")
@Validated
public class DecisionInstanceController extends ErrorController {

  public static final String URI = "/v1/decision-instances";
  public static final String BY_ID = "/{id}";

  @Autowired
  private DecisionInstanceDao decisionInstanceDao;

  @Operation(summary = "Get decision instance by id", security = {@SecurityRequirement(name = "bearer-key"), @SecurityRequirement(name = "cookie")}, tags = {
      "Decision"}, responses = {@ApiResponse(description = "Success", responseCode = "200"),
      @ApiResponse(description = ServerException.TYPE, responseCode = "500", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ClientException.TYPE, responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ResourceNotFoundException.TYPE, responseCode = "404", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class)))})
  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = BY_ID,
      produces = {MediaType.APPLICATION_JSON_VALUE}
  )
  public DecisionInstance byId(@PathVariable String id) {
    return decisionInstanceDao.byId(id);
  }
}
