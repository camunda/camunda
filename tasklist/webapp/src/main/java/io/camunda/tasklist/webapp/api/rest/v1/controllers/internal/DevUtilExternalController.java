/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.se.SearchEngineUserDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e-test")
@Tag(name = "Util", description = "This API it's just for internal use as dev utilities.")
@RestController
@RequestMapping(value = TasklistURIs.DEV_UTIL_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class DevUtilExternalController {

  @Autowired private SchemaManager schemaManager;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private DataGenerator devDataGenerator;

  @Autowired private SearchEngineUserDetailsService searchEngineUserDetailsService;

  @Autowired private ProcessCache processCache;

  @Operation(
      summary = "Get details about the current user.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true)
      })
  @PostMapping("recreateData")
  public ResponseEntity<?> recreateData() throws IOException {
    final DeleteIndexRequest deleteRequest = new DeleteIndexRequest();
    deleteRequest.indices("_all");
    esClient.indices().delete(deleteRequest, RequestOptions.DEFAULT);
    processCache.clearCache();
    schemaManager.createSchema();
    searchEngineUserDetailsService.initializeUsers();
    devDataGenerator.createDemoUsers();
    return ResponseEntity.ok().build();
  }
}
