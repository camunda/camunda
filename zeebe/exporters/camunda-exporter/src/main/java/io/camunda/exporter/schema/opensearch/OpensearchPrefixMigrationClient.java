/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema.opensearch;

import io.camunda.exporter.schema.PrefixMigrationClient;
import io.camunda.exporter.utils.CloneResult;
import io.camunda.exporter.utils.ReindexResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpType;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchPrefixMigrationClient implements PrefixMigrationClient {
  private static final Logger LOG = LoggerFactory.getLogger(OpensearchPrefixMigrationClient.class);
  private final static String dateRegexForIndices = "\\d{4}-\\d{2}-\\d{2}";
  private final OpenSearchClient client;

  public OpensearchPrefixMigrationClient(final OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public ReindexResult reindex(final String src, final String dest) {
    final var reindexRequest =
        new ReindexRequest.Builder()
            .source(s -> s.index(src))
            .dest(d -> d.index(dest).opType(OpType.Create))
            .build();

    try {
      LOG.info("Reindexing [{}] into [{}]", src, dest);
      client.reindex(reindexRequest);
      return new ReindexResult(true, src, dest, null);
    } catch (final IOException e) {
      LOG.error("Failed to reindex [{}] into [{}]", src, dest, e);
      return new ReindexResult(false, src, dest, e);
    }
  }

  @Override
  public CloneResult clone(final String source, final String destination) {
    try {
      LOG.info("Cloning [{}] to [{}]", source, destination);
      markIndexReadOnly(source);
      cloneIndex(source, destination);
      return new CloneResult(true, source, destination, null);
    } catch (final IOException e) {
      LOG.error("Error migrating index [{}] to [{}]", source, destination, e);
      return new CloneResult(false, source, destination, e);
    }
  }

  @Override
  public List<String> getAllHistoricIndices(final String prefix) {
    try {
      return client.cat().indices(i -> i.index(prefix + "*")).valueBody().stream()
          .map(IndicesRecord::index)
          .filter(index -> Pattern.matches(".*" + dateRegexForIndices + "$", index))
          .toList();
    } catch (final IOException e) {
      LOG.error("Failed to get all historic indices for prefix [{}]", prefix, e);
      throw new IllegalStateException(
          "Failed to retrieve historic indices for prefix [" + prefix + "]", e);
    }
  }

  private void markIndexReadOnly(final String index) throws IOException {
    client
        .indices()
        .putSettings(r -> r.index(index).settings(s -> s.index(i -> i.blocks(b -> b.write(true)))));
  }

  private void cloneIndex(final String src, final String target) throws IOException {
    final var targetAlias = target.replaceAll(dateRegexForIndices, "alias");
    client
        .indices()
        .clone(
            c ->
                c.index(src)
                    .target(target)
                    .settings(
                        Map.of(
                            "index.blocks.write",
                            JsonData.of(false),
                            "number_of_replicas",
                            JsonData.of(0)))
                    .aliases(targetAlias, new Alias.Builder().build()));
  }
}
