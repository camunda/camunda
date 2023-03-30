/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import io.camunda.tasklist.webapp.api.rest.v1.controllers.ApiErrorController;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.UserReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "This API enables querying user details.")
@RestController
@RequestMapping(value = TasklistURIs.USERS_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class UserInternalController extends ApiErrorController {

  @Autowired private UserReader userReader;

  @Operation(
      summary = "Get details about the current user.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true)
      })
  @GetMapping("current")
  public ResponseEntity<UserDTO> getCurrentUser() {
    return ResponseEntity.ok(userReader.getCurrentUser());
  }
}
