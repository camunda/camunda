/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.opensearch;

import io.camunda.search.schema.PrefixMigrationClient;
import io.camunda.search.schema.utils.CloneResult;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.update_aliases.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchPrefixMigrationClient implements PrefixMigrationClient {
  private static final Logger LOG = LoggerFactory.getLogger(OpensearchPrefixMigrationClient.class);
  private final OpenSearchClient client;
  private final OpenSearchAsyncClient asyncClient;

  public OpensearchPrefixMigrationClient(final OpenSearchClient client) {
    this.client = client;
    asyncClient = new OpenSearchAsyncClient(client._transport(), client._transportOptions());
  }

  @Override
  public List<String> getIndicesInAlias(final String alias) {
    try {
      final var aliasMatches =
          client.indices().getAlias(g -> g.name(alias)).result().keySet().stream().toList();
      LOG.info("Found {} indices for alias '{}'", aliasMatches, alias);
      return aliasMatches;
    } catch (final OpenSearchException esx) {
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
    try {
      return asyncClient
          .indices()
          .putSettings(
              r -> r.index(source).settings(s -> s.index(i -> i.blocks(b -> b.write(true)))))
          .thenCompose(
              ignore -> {
                LOG.info("Marked index [{}] as read-only", source);
                try {
                  return asyncClient.indices().clone(c -> c.index(source).target(destination));
                } catch (final IOException e) {
                  throw new IllegalStateException("Failed to clone index [" + source + "]", e);
                }
              })
          .thenCompose(
              ignore -> {
                LOG.info("Successfully cloned [{}] to [{}]", source, destination);
                try {
                  return asyncClient
                      .indices()
                      .updateAliases(
                          u ->
                              u.actions(
                                  Action.of(
                                      a ->
                                          a.remove(rm -> rm.index(destination).alias(sourceAlias))),
                                  Action.of(
                                      a ->
                                          a.add(
                                              add ->
                                                  add.index(destination)
                                                      .alias(destinationAlias)
                                                      .isWriteIndex(false)))));
                } catch (final IOException e) {
                  throw new IllegalStateException(
                      "Failed to update aliases for indices [" + source + ", " + destination + "]",
                      e);
                }
              })
          .thenCompose(
              ignore -> {
                LOG.info("Successfully updated aliases for [{}] index", destination);
                try {
                  return asyncClient.indices().delete(d -> d.index(source));
                } catch (final IOException e) {
                  throw new IllegalStateException("Failed to delete index [" + source + "]", e);
                }
              })
          .thenApply(
              ignore -> {
                LOG.info("Successfully deleted source index [{}]", source);
                return new CloneResult(true, source, destination, null);
              })
          .exceptionally(
              throwable -> {
                LOG.error(
                    "Failed to clone and delete index [{}] to [{}]",
                    source,
                    destination,
                    throwable);
                return new CloneResult(false, source, destination, throwable);
              });
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
