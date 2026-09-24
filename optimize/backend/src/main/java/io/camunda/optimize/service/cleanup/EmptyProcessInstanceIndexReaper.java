/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import io.camunda.optimize.service.db.DatabaseClient;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Deletes the process instance index of a definition key once history cleanup has emptied it. Every
 * index holds shards even when empty, and the cluster refuses to create any index once its shard
 * limit is reached, so empty indices for keys that are no longer used must not accumulate.
 *
 * <p>An index that gets new instances again is recreated by the importer, which only writes through
 * the index alias and creates the index when the alias is missing.
 */
@Component
public class EmptyProcessInstanceIndexReaper {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(EmptyProcessInstanceIndexReaper.class);

  private final DatabaseClient databaseClient;

  public EmptyProcessInstanceIndexReaper(final DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public void deleteIfEmpty(final String definitionKey) {
    final String alias = getProcessInstanceIndexAliasName(definitionKey);
    try {
      final var indices =
          databaseClient.getAllIndicesForAlias(databaseClient.convertToPrefixedAliasName(alias));
      if (indices.size() != 1) {
        return;
      }
      final String index = indices.iterator().next();
      if (databaseClient.countWithoutPrefix(index) > 0) {
        return;
      }

      // the block makes the second count final: an instance imported after the first count is
      // either visible to it or rejected, and the rejected write is retried by the importer
      databaseClient.addWriteBlock(index);
      try {
        databaseClient.refresh(alias);
        if (databaseClient.countWithoutPrefix(index) > 0) {
          databaseClient.removeWriteBlock(index);
          return;
        }
        databaseClient.deleteIndexByRawIndexNames(index);
        LOG.info("Deleted empty process instance index {} to release its shards.", index);
      } catch (final RuntimeException e) {
        databaseClient.removeWriteBlock(index);
        throw e;
      }
    } catch (final IOException | RuntimeException e) {
      LOG.warn(
          "Could not delete the empty process instance index of definition key {}; retrying on the"
              + " next cleanup run.",
          definitionKey,
          e);
    }
  }
}
