/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.qa.util.multidb.ElasticOpenSearchSetupHelper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationDatabaseChecks extends ElasticOpenSearchSetupHelper {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationDatabaseChecks.class);
  private static final String IMPORTERS_FINISHED_QUERY =
      """
            {
              "size": 0,
              "aggs": {
                "total_docs": {
                  "value_count": {
                    "field": "id"
                  }
                },
                "completed_docs": {
                  "filter": {
                    "term": {
                      "completed": true
                    }
                  }
                }
              }
            }
        """;

  public MigrationDatabaseChecks(
      final String endpoint, final Collection<IndexDescriptor> expectedDescriptors) {
    super(endpoint, expectedDescriptors);
  }

  public boolean checkImportersFinished(final String indexPrefix, final String component)
      throws IOException, InterruptedException {
    final String targetUrl =
        String.format("%s/%s-%s-import-position*/_search", endpoint, indexPrefix, component);

    final HttpResponse<String> response;
    LOGGER.info("Checking if all importers are marked as completed for {}", component);
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(IMPORTERS_FINISHED_QUERY))
            .build();

    response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());

      final int totalDocs =
          jsonResponse.path("aggregations").path("total_docs").path("value").asInt();
      final int trueDocs =
          jsonResponse.path("aggregations").path("completed_docs").path("doc_count").asInt();

      return totalDocs == trueDocs;
    }
    return false;
  }

  public boolean checkImportPositionsFlushed(final String indexPrefix, final String component)
      throws IOException, InterruptedException {
    final String targetUrl =
        String.format("%s/%s-%s-import-position*/_search", endpoint, indexPrefix, component);
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    LOGGER.info("Checking if import positions have been flushed for {}", component);
    final var response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());
      final int totalDocs = jsonResponse.path("hits").path("total").path("value").asInt();
      return totalDocs > 0;
    }
    return false;
  }
}
