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
import java.util.Arrays;
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
    return updateWriteBlock(source, true)
        .thenCompose(ignore -> cloneIndex(source, destination))
        .thenCompose(
            ignore -> updateAliasesAtomically(source, destination, sourceAlias, destinationAlias))
        .thenCompose(ignore -> updateWriteBlock(destination, false))
        .thenCompose(ignore -> deleteIndex(source))
        .thenApply(
            ignore -> {
              LOG.info("Successfully migrated index [{}] to [{}]", source, destination);
              return new CloneResult(true, source, destination, null);
            })
        .exceptionally(ex -> handleMigrationFailure(ex, source, destination));
  }

  @Override
  public CompletableFuture<Void> deleteIndex(final String... index) {
    return asyncClient
        .indices()
        .delete(d -> d.index(Arrays.asList(index)))
        .thenRun(() -> LOG.info("Deleted indices [{}]", Arrays.stream(index).toList()));
  }

  @Override
  public CompletableFuture<Void> deleteComponentTemplate(final String componentTemplateName) {
    return asyncClient
        .cluster()
        .deleteComponentTemplate(d -> d.name(componentTemplateName))
        .thenRun(() -> LOG.info("Deleted component template [{}]", componentTemplateName));
  }

  @Override
  public CompletableFuture<Void> deleteIndexTemplate(final String indexTemplateName) {
    return asyncClient
        .indices()
        .deleteIndexTemplate(d -> d.name(indexTemplateName))
        .thenRun(() -> LOG.info("Deleted index template [{}]", indexTemplateName));
  }

  private CompletableFuture<Void> cloneIndex(final String source, final String destination) {
    return asyncClient
        .indices()
        .clone(c -> c.index(source).target(destination))
        .thenRun(() -> LOG.info("Cloned index [{}] to [{}]", source, destination));
  }

  private CompletableFuture<Void> updateAliasesAtomically(
      final String source,
      final String destination,
      final String sourceAlias,
      final String destinationAlias) {
    return asyncClient
        .indices()
        .updateAliases(
            u ->
                u.actions(
                    Action.of(a -> a.remove(rm -> rm.index(destination).alias(sourceAlias))),
                    Action.of(
                        a ->
                            a.add(
                                add ->
                                    add.index(destination)
                                        .alias(destinationAlias)
                                        .isWriteIndex(false)))))
        .thenRun(
            () ->
                LOG.info(
                    "Updated aliases: removed [{}] from [{}], added [{}] to [{}]",
                    sourceAlias,
                    source,
                    destinationAlias,
                    destination));
  }

  private CompletableFuture<Void> updateWriteBlock(final String index, final boolean block) {
    return asyncClient
        .indices()
        .putSettings(r -> r.index(index).settings(s -> s.index(i -> i.blocks(b -> b.write(block)))))
        .thenRun(() -> LOG.info("Updated setting index.blocks.write: {}, for [{}]", block, index));
  }

  private CloneResult handleMigrationFailure(
      final Throwable throwable, final String source, final String destination) {
    LOG.error("Failed to migrate index [{}] to [{}]", source, destination, throwable);
    return new CloneResult(false, source, destination, throwable);
  }
}
