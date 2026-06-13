/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.WriteResponseBase.AbstractBuilder;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.ObjectBuilderDeserializer;
import co.elastic.clients.transport.endpoints.SimpleEndpoint;

/**
 * Provides an alias-aware {@link IndexResponse} deserializer that recognises underscore-prefixed
 * field aliases ({@code _shard}, {@code _index}, {@code _node}) in {@code _shards.failures[*]}
 * entries. ES Java client 8.16.6 only registers the canonical names and throws {@code
 * MissingRequiredPropertyException} when the server returns the prefixed variants (issue #45809).
 *
 * <p>Fixed upstream in 8.18.6 and 8.19.13. Until the client is upgraded, this custom deserializer
 * registers both the canonical and underscore-prefixed names for the affected fields.
 */
final class AliasAwareIndexResponseDeserializer {

  // ShardFailure with underscore-prefixed aliases registered for shard, index, and node.
  static final JsonpDeserializer<ShardFailure> SHARD_FAILURE =
      ObjectBuilderDeserializer.lazy(
          ShardFailure.Builder::new,
          op -> {
            op.add(
                ShardFailure.Builder::index,
                JsonpDeserializer.stringDeserializer(),
                "index",
                "_index");
            op.add(
                ShardFailure.Builder::node,
                JsonpDeserializer.stringDeserializer(),
                "node",
                "_node");
            op.add(ShardFailure.Builder::reason, ErrorCause._DESERIALIZER, "reason");
            op.add(
                ShardFailure.Builder::shard,
                JsonpDeserializer.integerDeserializer(),
                "shard",
                "_shard");
            op.add(ShardFailure.Builder::status, JsonpDeserializer.stringDeserializer(), "status");
          });

  // ShardStatistics wired to use the alias-aware ShardFailure deserializer.
  static final JsonpDeserializer<ShardStatistics> SHARD_STATISTICS =
      ObjectBuilderDeserializer.lazy(
          ShardStatistics.Builder::new,
          op -> {
            op.add(
                ShardStatistics.Builder::failed, JsonpDeserializer.numberDeserializer(), "failed");
            op.add(
                ShardStatistics.Builder::successful,
                JsonpDeserializer.numberDeserializer(),
                "successful");
            op.add(ShardStatistics.Builder::total, JsonpDeserializer.numberDeserializer(), "total");
            op.add(
                ShardStatistics.Builder::failures,
                JsonpDeserializer.arrayDeserializer(SHARD_FAILURE),
                "failures");
            op.add(
                ShardStatistics.Builder::skipped,
                JsonpDeserializer.numberDeserializer(),
                "skipped");
          });

  // Full IndexResponse deserializer that wires the alias-aware ShardStatistics for _shards.
  // Replicates WriteResponseBase.setupWriteResponseBaseDeserializer (protected, inaccessible here).
  static final JsonpDeserializer<IndexResponse> INDEX_RESPONSE =
      ObjectBuilderDeserializer.lazy(
          IndexResponse.Builder::new,
          op -> {
            op.add(AbstractBuilder::id, JsonpDeserializer.stringDeserializer(), "_id");
            op.add(AbstractBuilder::index, JsonpDeserializer.stringDeserializer(), "_index");
            op.add(
                AbstractBuilder::primaryTerm,
                JsonpDeserializer.longDeserializer(),
                "_primary_term");
            op.add(AbstractBuilder::result, Result._DESERIALIZER, "result");
            op.add(AbstractBuilder::seqNo, JsonpDeserializer.longDeserializer(), "_seq_no");
            op.add(AbstractBuilder::shards, SHARD_STATISTICS, "_shards");
            op.add(AbstractBuilder::version, JsonpDeserializer.longDeserializer(), "_version");
            op.add(
                AbstractBuilder::forcedRefresh,
                JsonpDeserializer.booleanDeserializer(),
                "forced_refresh");
          });

  @SuppressWarnings("unchecked")
  static final SimpleEndpoint<IndexRequest<?>, IndexResponse> ENDPOINT =
      ((SimpleEndpoint<IndexRequest<?>, IndexResponse>) IndexRequest._ENDPOINT)
          .withResponseDeserializer(INDEX_RESPONSE);

  private AliasAwareIndexResponseDeserializer() {}
}
