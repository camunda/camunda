/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.range;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.FilterBase;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class DateValueFilterTransformer implements FilterTransformer<DateFieldFilter> {

  private final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");

  @Override
  public SearchQuery toSearchQuery(final DateFieldFilter filter) {
    final var field = Objects.requireNonNull(filter.field());
    final var dateFiler = filter.filter();
    final var after = dateFiler.after();
    final var before = dateFiler.before();

    final var builder = range().field(field);

    if (after != null) {
      builder.gte(formatDate(after));
    }

    if (before != null) {
      builder.lt(formatDate(before));
    }

    return builder.format("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").build().toSearchQuery();
  }

  private String formatDate(final OffsetDateTime date) {
    return dateTimeFormatter.format(date);
  }

  public static final record DateFieldFilter(String field, DateValueFilter filter)
      implements FilterBase {}
}
