/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestUtil {

  public static final String DATE_TIME_GRAPHQL_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSxxxx";
  private static final Logger LOGGER = LoggerFactory.getLogger(TestUtil.class);

  private TestUtil() {}

  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static void removeAllIndices(OpenSearchClient osClient, String prefix) {
    try {
      LOGGER.info("Removing indices");
      final var indexResponses = osClient.indices().get(ir -> ir.index(List.of(prefix + "*")));
      final List listIndexResponses = indexResponses.result().keySet().stream().toList();
      if (listIndexResponses.size() > 0) {
        osClient.indices().delete(d -> d.index(listIndexResponses));
      }

      final var templateResponses =
          osClient.indices().getIndexTemplate(it -> it.name(prefix + "*"));

      templateResponses.indexTemplates().stream()
          .forEach(
              t -> {
                try {
                  osClient.indices().deleteIndexTemplate(dit -> dit.name(t.name()));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });

    } catch (IOException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static void removeAllIndices(RestHighLevelClient esClient, String prefix) {
    try {
      LOGGER.info("Removing indices");
      final var indexResponses =
          esClient.indices().get(new GetIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      for (String index : indexResponses.getIndices()) {
        esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
      }
      final var templateResponses =
          esClient
              .indices()
              .getIndexTemplate(
                  new GetComposableIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
      for (String template : templateResponses.getIndexTemplates().keySet()) {
        esClient
            .indices()
            .deleteIndexTemplate(
                new DeleteComposableIndexTemplateRequest(template), RequestOptions.DEFAULT);
      }
    } catch (ElasticsearchStatusException | IOException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static boolean isElasticSearch() {
    return !isOpenSearch();
  }

  public static boolean isOpenSearch() {
    final String databaseType = Optional.ofNullable(
        System.getProperty("camunda.data.secondary-storage.type")).orElse("elasticsearch");
    return "opensearch".equalsIgnoreCase(databaseType);
  }
}
