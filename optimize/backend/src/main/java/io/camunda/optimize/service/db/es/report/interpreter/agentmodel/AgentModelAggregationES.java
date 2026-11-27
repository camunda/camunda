/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.agentmodel;

import static io.camunda.optimize.service.db.DatabaseConstants.AGGREGATION_FIELD_KEY;
import static io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys.MODEL_AGGREGATION;
import static io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys.PROVIDER_AGGREGATION;
import static io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys.UNKNOWN;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Buckets agent runs by provider, then model, and reads the buckets back per model key. */
public final class AgentModelAggregationES {

  private AgentModelAggregationES() {}

  public static Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final int bucketLimit,
      final Map<String, Aggregation.Builder.ContainerBuilder> subAggregations) {
    final Aggregation.Builder.ContainerBuilder byModel = termsOn(AgentRunIndex.MODEL, bucketLimit);
    subAggregations.forEach((name, aggregation) -> byModel.aggregations(name, aggregation.build()));
    final Aggregation.Builder.ContainerBuilder byProvider =
        termsOn(AgentRunIndex.PROVIDER, bucketLimit)
            .aggregations(MODEL_AGGREGATION, byModel.build());
    return Map.of(PROVIDER_AGGREGATION, byProvider);
  }

  public static List<ModelBucket> modelBuckets(final Map<String, Aggregate> aggregations) {
    final List<ModelBucket> buckets = new ArrayList<>();
    final Aggregate byProvider =
        aggregations == null ? null : aggregations.get(PROVIDER_AGGREGATION);
    if (byProvider == null) {
      return buckets;
    }
    for (final StringTermsBucket provider : byProvider.sterms().buckets().array()) {
      for (final StringTermsBucket model :
          provider.aggregations().get(MODEL_AGGREGATION).sterms().buckets().array()) {
        final String modelName = model.key().stringValue();
        buckets.add(
            new ModelBucket(
                AgentModelKeys.key(provider.key().stringValue(), modelName),
                modelName,
                model.aggregations()));
      }
    }
    return buckets;
  }

  private static Aggregation.Builder.ContainerBuilder termsOn(
      final String field, final int bucketLimit) {
    return new Aggregation.Builder()
        .terms(
            t ->
                t.field(field)
                    .missing(UNKNOWN)
                    .size(bucketLimit)
                    .order(NamedValue.of(AGGREGATION_FIELD_KEY, SortOrder.Asc)));
  }

  public record ModelBucket(String key, String label, Map<String, Aggregate> aggregations) {}
}
