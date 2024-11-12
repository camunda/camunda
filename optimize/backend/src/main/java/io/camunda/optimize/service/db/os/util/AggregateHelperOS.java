/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.util;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.json;

import io.camunda.optimize.util.types.MapUtil;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.AutoDateHistogramAggregate;
import org.opensearch.client.opensearch._types.aggregations.AvgAggregate;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregate;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.DateRangeAggregate;
import org.opensearch.client.opensearch._types.aggregations.DoubleTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.DoubleTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.HistogramAggregate;
import org.opensearch.client.opensearch._types.aggregations.HistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.LongTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.MaxAggregate;
import org.opensearch.client.opensearch._types.aggregations.MinAggregate;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketAggregateBase;
import org.opensearch.client.opensearch._types.aggregations.MultiTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.MultiTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.RangeAggregate;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.aggregations.ReverseNestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.SumAggregate;

public final class AggregateHelperOS {

  private AggregateHelperOS() {}

  /* It is a suggested workaround from ES (and OS inherits it) to distinguish between 0 and null
   * values in aggregates based on doc count in a bucket (if doc count is 0 value is supposed to be
   * null,otherwise it is real 0). As they say in ES this way of interpreting 0 value is caused by
   * ES client design decision.
   *
   * However, considering we are not passing around buckets in reporting but aggregates, in order
   * to apply the suggested workaround we populate aggregate's meta field "isNull" with true
   * if bucket's doc count is 0 and aggregate value is also 0 and false otherwise.
   *
   * See
   * https://discuss.elastic.co/t/java-api-client-single-metric-aggregation-zero-or-null-deserializer/356207
   */
  private static Aggregate withNullValue(Aggregate aggregate, final long docCount) {
    aggregate = fixAggregates(aggregate);

    if (docCount > 0) {
      return aggregate;
    }

    if (aggregate.isAvg() && aggregate.avg().value() == 0) {
      return new AvgAggregate.Builder().meta("isNull", json(true)).value(0).build()._toAggregate();
    } else if (aggregate.isMax() && aggregate.max().value() == 0) {
      return new MaxAggregate.Builder().meta("isNull", json(true)).value(0).build()._toAggregate();
    } else if (aggregate.isMin()) {
      return new MinAggregate.Builder().meta("isNull", json(true)).value(0).build()._toAggregate();
    } else if (aggregate.isSum()) {
      return new SumAggregate.Builder().meta("isNull", json(true)).value(0).build()._toAggregate();
    } else {
      return aggregate;
    }
  }

  public static Map<String, Aggregate> withNullValues(
      final long docCount, final Map<String, Aggregate> aggregations) {
    return aggregations.entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), withNullValue(entry.getValue(), docCount)))
        .collect(MapUtil.pairCollector());
  }

  private static Aggregate fixAggregates(final Aggregate aggregate) {
    if (aggregate.isAutoDateHistogram()) {
      return AggregateBuilder.of(aggregate.autoDateHistogram())
          .buckets(
              copyBuckets(
                  aggregate.autoDateHistogram(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isComposite()) {
      return AggregateBuilder.of(aggregate.composite())
          .buckets(
              copyBuckets(
                  aggregate.composite(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isDateHistogram()) {
      return AggregateBuilder.of(aggregate.dateHistogram())
          .buckets(
              copyBuckets(
                  aggregate.dateHistogram(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isDateRange()) {
      return AggregateBuilder.of(aggregate.dateRange())
          .buckets(
              copyBuckets(
                  aggregate.dateRange(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isDterms()) {
      return AggregateBuilder.of(aggregate.dterms())
          .buckets(
              copyBuckets(
                  aggregate.dterms(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isFilter()) {
      return AggregateBuilder.of(aggregate.filter())
          .aggregations(
              withNullValues(aggregate.filter().docCount(), aggregate.filter().aggregations()))
          .build()
          ._toAggregate();
    } else if (aggregate.isHistogram()) {
      return AggregateBuilder.of(aggregate.histogram())
          .buckets(
              copyBuckets(
                  aggregate.histogram(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isLterms()) {
      return AggregateBuilder.of(aggregate.lterms())
          .buckets(
              copyBuckets(
                  aggregate.lterms(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isMultiTerms()) {
      return AggregateBuilder.of(aggregate.multiTerms())
          .buckets(
              copyBuckets(
                  aggregate.multiTerms(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isNested()) {
      return AggregateBuilder.of(aggregate.nested())
          .aggregations(
              withNullValues(aggregate.nested().docCount(), aggregate.nested().aggregations()))
          .build()
          ._toAggregate();
    } else if (aggregate.isReverseNested()) {
      return AggregateBuilder.of(aggregate.reverseNested())
          .aggregations(
              withNullValues(
                  aggregate.reverseNested().docCount(), aggregate.reverseNested().aggregations()))
          .build()
          ._toAggregate();
    } else if (aggregate.isRange()) {
      return AggregateBuilder.of(aggregate.range())
          .buckets(
              copyBuckets(
                  aggregate.range(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else if (aggregate.isSterms()) {
      return AggregateBuilder.of(aggregate.sterms())
          .buckets(
              copyBuckets(
                  aggregate.sterms(),
                  bucket ->
                      BucketBuilder.of(bucket)
                          .aggregations(withNullValues(bucket.docCount(), bucket.aggregations()))
                          .build()))
          .build()
          ._toAggregate();
    } else {
      return aggregate;
    }
  }

  private static <BUCKET, AGGREGATE extends MultiBucketAggregateBase<BUCKET>>
      Buckets<BUCKET> copyBuckets(
          final AGGREGATE aggregate, final Function<BUCKET, BUCKET> copyBucket) {
    final List<BUCKET> bucketList = aggregate.buckets().array().stream().map(copyBucket).toList();
    return new Buckets.Builder<BUCKET>().array(bucketList).build();
  }

  static class AggregateBuilder {

    private static AutoDateHistogramAggregate.Builder of(
        final AutoDateHistogramAggregate aggregate) {
      return new AutoDateHistogramAggregate.Builder()
          .buckets(aggregate.buckets())
          .interval(aggregate.interval())
          .meta(aggregate.meta());
    }

    private static CompositeAggregate.Builder of(final CompositeAggregate aggregate) {
      return new CompositeAggregate.Builder()
          .buckets(aggregate.buckets())
          .afterKey(aggregate.afterKey())
          .meta(aggregate.meta());
    }

    private static DateHistogramAggregate.Builder of(final DateHistogramAggregate aggregate) {
      return new DateHistogramAggregate.Builder()
          .buckets(aggregate.buckets())
          .meta(aggregate.meta());
    }

    private static DateRangeAggregate.Builder of(final DateRangeAggregate aggregate) {
      return new DateRangeAggregate.Builder().buckets(aggregate.buckets()).meta(aggregate.meta());
    }

    private static DoubleTermsAggregate.Builder of(final DoubleTermsAggregate aggregate) {
      return new DoubleTermsAggregate.Builder()
          .buckets(aggregate.buckets())
          .docCountErrorUpperBound(aggregate.docCountErrorUpperBound())
          .meta(aggregate.meta())
          .sumOtherDocCount(aggregate.sumOtherDocCount());
    }

    private static FilterAggregate.Builder of(final FilterAggregate aggregate) {
      return new FilterAggregate.Builder()
          .aggregations(withNullValues(aggregate.docCount(), aggregate.aggregations()))
          .docCount(aggregate.docCount())
          .meta(aggregate.meta());
    }

    private static HistogramAggregate.Builder of(final HistogramAggregate aggregate) {
      return new HistogramAggregate.Builder().buckets(aggregate.buckets()).meta(aggregate.meta());
    }

    private static LongTermsAggregate.Builder of(final LongTermsAggregate aggregate) {
      return new LongTermsAggregate.Builder()
          .buckets(aggregate.buckets())
          .docCountErrorUpperBound(aggregate.docCountErrorUpperBound())
          .meta(aggregate.meta())
          .sumOtherDocCount(aggregate.sumOtherDocCount());
    }

    private static MultiTermsAggregate.Builder of(final MultiTermsAggregate aggregate) {
      return new MultiTermsAggregate.Builder()
          .buckets(aggregate.buckets())
          .docCountErrorUpperBound(aggregate.docCountErrorUpperBound())
          .sumOtherDocCount(aggregate.sumOtherDocCount())
          .meta(aggregate.meta());
    }

    private static NestedAggregate.Builder of(final NestedAggregate aggregate) {
      return new NestedAggregate.Builder()
          .aggregations(aggregate.aggregations())
          .docCount(aggregate.docCount())
          .meta(aggregate.meta());
    }

    private static ReverseNestedAggregate.Builder of(final ReverseNestedAggregate aggregate) {
      return new ReverseNestedAggregate.Builder()
          .aggregations(aggregate.aggregations())
          .docCount(aggregate.docCount())
          .meta(aggregate.meta());
    }

    private static RangeAggregate.Builder of(final RangeAggregate aggregate) {
      return new RangeAggregate.Builder().buckets(aggregate.buckets()).meta(aggregate.meta());
    }

    private static StringTermsAggregate.Builder of(final StringTermsAggregate aggregate) {
      return new StringTermsAggregate.Builder()
          .buckets(aggregate.buckets())
          .docCountErrorUpperBound(aggregate.docCountErrorUpperBound())
          .meta(aggregate.meta())
          .sumOtherDocCount(aggregate.sumOtherDocCount());
    }
  }

  static class BucketBuilder {

    private static CompositeBucket.Builder of(final CompositeBucket bucket) {
      return new CompositeBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .key(bucket.key());
    }

    private static DateHistogramBucket.Builder of(final DateHistogramBucket bucket) {
      return new DateHistogramBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .key(bucket.key())
          .keyAsString(bucket.keyAsString());
    }

    private static DoubleTermsBucket.Builder of(final DoubleTermsBucket bucket) {
      return new DoubleTermsBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .key(bucket.key());
    }

    private static HistogramBucket.Builder of(final HistogramBucket bucket) {
      return new HistogramBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .key(bucket.key())
          .keyAsString(bucket.keyAsString());
    }

    private static LongTermsBucket.Builder of(final LongTermsBucket bucket) {
      return new LongTermsBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .key(bucket.key());
    }

    private static MultiTermsBucket.Builder of(final MultiTermsBucket bucket) {
      return new MultiTermsBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .docCountErrorUpperBound(bucket.docCountErrorUpperBound())
          .key(bucket.key())
          .keyAsString(bucket.keyAsString());
    }

    private static RangeBucket.Builder of(final RangeBucket bucket) {
      return new RangeBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .from(bucket.from())
          .fromAsString(bucket.fromAsString())
          .key(bucket.key())
          .to(bucket.to())
          .toAsString(bucket.toAsString());
    }

    private static StringTermsBucket.Builder of(final StringTermsBucket bucket) {
      return new StringTermsBucket.Builder()
          .aggregations(bucket.aggregations())
          .docCount(bucket.docCount())
          .key(bucket.key());
    }
  }
}
