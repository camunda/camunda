/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.agentmodel;

import static io.camunda.optimize.service.db.DatabaseConstants.AGGREGATION_FIELD_KEY;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys.MODEL_AGGREGATION;
import static io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys.PROVIDER_AGGREGATION;
import static io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys.UNKNOWN;

import io.camunda.optimize.service.db.report.interpreter.agentmodel.AgentModelKeys;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

/** Buckets agent runs by provider, then model, and reads the buckets back per model key. */
public final class AgentModelAggregationOS {

  private AgentModelAggregationOS() {}

  public static Map<String, Aggregation> createAggregation(
      final int bucketLimit, final Map<String, Aggregation> subAggregations) {
    final Aggregation byModel =
        withSubaggregations(termsOn(AgentRunIndex.MODEL, bucketLimit), subAggregations);
    final Aggregation byProvider =
        withSubaggregations(
            termsOn(AgentRunIndex.PROVIDER, bucketLimit), Map.of(MODEL_AGGREGATION, byModel));
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
        buckets.add(
            new ModelBucket(
                AgentModelKeys.key(provider.key(), model.key()),
                model.key(),
                model.aggregations()));
      }
    }
    return buckets;
  }

  private static TermsAggregation termsOn(final String field, final int bucketLimit) {
    return TermsAggregation.of(
        t ->
            t.field(field)
                .missing(FieldValue.of(UNKNOWN))
                .size(bucketLimit)
                .order(Map.of(AGGREGATION_FIELD_KEY, SortOrder.Asc)));
  }

  public record ModelBucket(String key, String label, Map<String, Aggregate> aggregations) {}
}
