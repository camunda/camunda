/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.util.DurationUtil;
import io.camunda.util.ObjectBuilder;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * A date-histogram aggregation that buckets documents by time.
 *
 * <p>Use {@link DateHistogramInterval.Fixed} for arbitrary durations (e.g. {@code "5m"}) or {@link
 * DateHistogramInterval.Calendar} for calendar-aligned buckets (e.g. day, week, month).
 */
public record SearchDateHistogramAggregator(
    String name, String field, DateHistogramInterval interval, List<SearchAggregator> aggregations)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder>
      implements ObjectBuilder<SearchDateHistogramAggregator> {

    private String field;
    private DateHistogramInterval interval;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder field(final String value) {
      field = value;
      return this;
    }

    /**
     * Sets an interval of fixed duration (e.g. {@code "5m"}, {@code "1h"}) or a calendar-aligned
     * unit (e.g. day, month).
     *
     * @see DateHistogramInterval for details.
     */
    public Builder interval(final DateHistogramInterval value) {
      interval = value;
      return this;
    }

    @Override
    public SearchDateHistogramAggregator build() {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(field, "field must not be null");
      Objects.requireNonNull(interval, "interval must not be null");
      return new SearchDateHistogramAggregator(name, field, interval, aggregations);
    }
  }

  /**
   * Sealed type representing the two kinds of date-histogram intervals.
   *
   * <ul>
   *   <li>{@link Fixed} — arbitrary fixed duration (e.g. {@code "5m"}, {@code "1h"}).
   *   <li>{@link Calendar} — calendar-aligned unit (second, minute, …, year).
   * </ul>
   */
  public sealed interface DateHistogramInterval
      permits DateHistogramInterval.Fixed, DateHistogramInterval.Calendar {

    /**
     * A fixed-duration interval expressed as a {@link Duration}.
     *
     * <p>Call {@link #toExpression()} to obtain the ES/OS notation string (e.g. {@code "5m"}). The
     * duration is converted to the coarsest exact unit (days → hours → minutes → seconds →
     * milliseconds). Sub-millisecond durations are rejected.
     */
    record Fixed(Duration duration) implements DateHistogramInterval {

      public Fixed {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative() || duration.isZero()) {
          throw new IllegalArgumentException("duration must be positive");
        }
        if (!duration.truncatedTo(ChronoUnit.MILLIS).equals(duration)) {
          throw new IllegalArgumentException(
              "duration has sub-millisecond precision, which is not supported by ES/OS fixed intervals");
        }
      }

      /** Returns the ES/OS fixed-interval expression, e.g. {@code "5m"} or {@code "2h"}. */
      public String toExpression() {
        return DurationUtil.toEsOsInterval(duration);
      }
    }

    /**
     * A calendar-aligned interval unit.
     *
     * <p>Each constant carries both the human-readable name used by the search engines and the
     * canonical short expression (e.g. {@code "1d"} for {@link #Day}).
     */
    enum Calendar implements DateHistogramInterval {
      Second("second", "1s"),
      Minute("minute", "1m"),
      Hour("hour", "1h"),
      Day("day", "1d"),
      Week("week", "1w"),
      Month("month", "1M"),
      Quarter("quarter", "1q"),
      Year("year", "1y");

      private final String name;
      private final String expression;

      Calendar(final String name, final String expression) {
        this.name = name;
        this.expression = expression;
      }

      /** Returns the name used by ES / OS (e.g. {@code "day"}). */
      public String getName() {
        return name;
      }

      /** Returns the canonical short expression (e.g. {@code "1d"}). */
      public String getExpression() {
        return expression;
      }
    }
  }
}
