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
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
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

  @Autowired private ProcessCache processCache;

  @Autowired private SearchEngineConfiguration configuration;

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
    final var connector = new ElasticsearchConnector(configuration.connect());
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), true);
    final var indicesToDelete =
        List.of(
            getIndexFullQualifiedName(DraftTaskVariableTemplate::new),
            getIndexFullQualifiedName(FlowNodeInstanceTemplate::new),
            getIndexFullQualifiedName(ListViewTemplate::new),
            getIndexFullQualifiedName(SnapshotTaskVariableTemplate::new),
            getIndexFullQualifiedName(TaskTemplate::new),
            getIndexFullQualifiedName(TasklistImportPositionIndex::new),
            getIndexFullQualifiedName(TasklistMetricIndex::new),
            getIndexFullQualifiedName(UsageMetricTUIndex::new),
            getIndexFullQualifiedName(VariableTemplate::new));

    try (final var elasticsearchClient = connector.createClient()) {
      final var searchEngineClient =
          new ElasticsearchEngineClient(elasticsearchClient, connector.objectMapper());
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

  private <T extends AbstractIndexDescriptor> String getIndexFullQualifiedName(
      final BiFunction<String, Boolean, T> constructor) {
    return constructor.apply(configuration.connect().getIndexPrefix(), true).getFullQualifiedName();
  }
}
