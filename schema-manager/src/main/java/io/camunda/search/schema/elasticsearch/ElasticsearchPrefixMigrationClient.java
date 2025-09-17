/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import io.camunda.search.schema.PrefixMigrationClient;
import io.camunda.search.schema.utils.CloneResult;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchPrefixMigrationClient implements PrefixMigrationClient {
  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchPrefixMigrationClient.class);

  private final ElasticsearchClient client;
  private final ElasticsearchAsyncClient asyncClient;

  public ElasticsearchPrefixMigrationClient(final ElasticsearchClient client) {
    this.client = client;
    asyncClient = new ElasticsearchAsyncClient(client._transport(), client._transportOptions());
  }

  @Override
  public List<String> getIndicesInAlias(final String alias) {
    try {
      final var aliasMatches =
          client.indices().getAlias(g -> g.name(alias)).result().keySet().stream().toList();
      LOG.info("Found {} indices for alias '{}'", aliasMatches, alias);
      return aliasMatches;
    } catch (final ElasticsearchException esx) {
      if (esx.status() == 404) {
        LOG.warn("No indices for alias '{}' exist", alias);
        return List.of();
      }
      LOG.error("Failed to get all aliased indices for alias [{}]", alias, esx);
      throw new IllegalStateException(
          "Failed to retrieve aliased indices for alias [" + alias + "]", esx);
    } catch (final IOException e) {
      LOG.error("Failed to get all aliased indices for alias [{}]", alias, e);
      throw new IllegalStateException(
          "Failed to retrieve aliased indices for alias [" + alias + "]", e);
    }
  }

  @Override
  public CompletableFuture<CloneResult> cloneAndDeleteIndex(
      final String source,
      final String sourceAlias,
      final String destination,
      final String destinationAlias) {
    return asyncClient
        .indices()
        .putSettings(r -> r.index(source).settings(s -> s.index(i -> i.blocks(b -> b.write(true)))))
        .thenCompose(
            ignore -> {
              LOG.info("Marked index [{}] as read-only", source);
              return asyncClient.indices().clone(c -> c.index(source).target(destination));
            })
        .thenCompose(
            ignore -> {
              LOG.info("Cloned index [{}] to [{}]", source, destination);
              return asyncClient
                  .indices()
                  .updateAliases(
                      u ->
                          u.actions(
                              Action.of(
                                  a -> a.remove(rm -> rm.index(destination).alias(sourceAlias))),
                              Action.of(
                                  a ->
                                      a.add(
                                          add ->
                                              add.index(destination)
                                                  .alias(destinationAlias)
                                                  .isWriteIndex(false)))));
            })
        .thenCompose(
            ignore -> {
              LOG.info("Updated aliases for [{}] index", destination);
              return asyncClient.indices().delete(d -> d.index(source));
            })
        .thenApply(
            ignore -> {
              LOG.info("Deleted index [{}]", source);
              return new CloneResult(true, source, destination, null);
            })
        .exceptionally(
            throwable -> {
              LOG.error("Failed to migrate index [{}] to [{}]", source, destination, throwable);
              return new CloneResult(false, source, destination, throwable);
            });
  }
}
