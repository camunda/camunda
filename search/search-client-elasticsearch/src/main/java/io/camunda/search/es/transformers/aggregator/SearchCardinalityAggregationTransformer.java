package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregation;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Optional;

public final class SearchCardinalityAggregationTransformer
    extends AggregatorTransformer<SearchCardinalityAggregator, Aggregation> {

  public SearchCardinalityAggregationTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchCardinalityAggregator value) {
    // Create the CardinalityAggregation
    final CardinalityAggregation.Builder cardinalityBuilder = AggregationBuilders.cardinality();
    Optional.ofNullable(value.field()).ifPresent(cardinalityBuilder::field);
    final var builder = new Aggregation.Builder().cardinality(cardinalityBuilder.build());
    applySubAggregations(builder, value);
    return builder.build();
  }
}
