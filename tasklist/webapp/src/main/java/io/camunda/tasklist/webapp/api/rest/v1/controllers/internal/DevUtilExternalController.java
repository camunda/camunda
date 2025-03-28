/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

  @Autowired private ProcessCache processCache;

  @Autowired
  @Qualifier("tasklistProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private SearchEngineConfiguration configuration;

  @Autowired private FormIndex formIndex;

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

    final var connector = new ElasticsearchConnector(configuration.connect());
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), true);

    try (final var elasticsearchClient = connector.createClient()) {
      final var searchEngineClient =
          new ElasticsearchEngineClient(elasticsearchClient, connector.objectMapper());
      final List<String> indicesToDelete =
          indexDescriptors.indices().stream()
              .map(IndexDescriptor::getFullQualifiedName)
              .filter(
                  f ->
                      !f.equals(processIndex.getFullQualifiedName())
                          && !f.equals(formIndex.getFullQualifiedName()))
              .toList();
      elasticsearchClient.indices().delete(r -> r.index(indicesToDelete));
      processCache.clearCache();

      final var schemaManager =
          new SchemaManager(
              searchEngineClient,
              indexDescriptors.indices(),
              indexDescriptors.templates(),
              configuration,
              connector.objectMapper());
      schemaManager.startup();
    }
    return ResponseEntity.ok().build();
  }
}
