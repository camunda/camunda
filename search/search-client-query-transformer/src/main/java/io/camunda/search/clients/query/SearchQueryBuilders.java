/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.util.CollectionUtil.collectValues;
import static io.camunda.util.CollectionUtil.withoutNull;

import io.camunda.search.clients.query.SearchHasParentQuery.Builder;
import io.camunda.search.clients.query.SearchMatchQuery.SearchMatchQueryOperator;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.ValueTypeEnum;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchQueryBuilders {

  public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);

  private SearchQueryBuilders() {}

  private static SearchQuery must(final List<SearchQuery> queries) {
    return bool().must(queries).build().toSearchQuery();
  }

  private static SearchQuery should(final List<SearchQuery> queries) {
    return bool().should(queries).build().toSearchQuery();
  }

  private static SearchQuery mustNot(final List<SearchQuery> queries) {
    return bool().mustNot(queries).build().toSearchQuery();
  }

  private static SearchQuery mustNot(final SearchQuery... queries) {
    return bool().mustNot(List.of(queries)).build().toSearchQuery();
  }

  private static SearchQuery map(
      final List<SearchQuery> queries, final Function<List<SearchQuery>, SearchQuery> mapper) {
    final var nonNullQueries = withoutNull(queries);
    if (nonNullQueries == null || nonNullQueries.isEmpty()) {
      return null;
    } else if (nonNullQueries.size() == 1) {
      return nonNullQueries.getFirst();
    } else {
      return mapper.apply(nonNullQueries);
    }
  }

  public static SearchBoolQuery.Builder bool() {
    return new SearchBoolQuery.Builder();
  }

  public static SearchBoolQuery bool(
      final Function<SearchBoolQuery.Builder, ObjectBuilder<SearchBoolQuery>> fn) {
    return fn.apply(bool()).build();
  }

  public static SearchQuery and(final SearchQuery query, final SearchQuery... queries) {
    return and(collectValues(query, queries));
  }

  public static SearchQuery and(final List<SearchQuery> queries) {
    return map(queries, SearchQueryBuilders::must);
  }

  @SafeVarargs
  public static SearchQuery and(final List<SearchQuery>... queries) {
    return map(Arrays.stream(queries).flatMap(List::stream).toList(), SearchQueryBuilders::must);
  }

  public static SearchQuery not(final SearchQuery query, final SearchQuery... queries) {
    return not(collectValues(query, queries));
  }

  public static SearchQuery not(final List<SearchQuery> queries) {
    final var nonNullQueries = withoutNull(queries);
    if (nonNullQueries != null && !nonNullQueries.isEmpty()) {
      return SearchQueryBuilders.mustNot(queries);
    }
    return null;
  }

  public static SearchQuery or(final SearchQuery query, final SearchQuery... queries) {
    return or(collectValues(query, queries));
  }

  public static SearchQuery or(final List<SearchQuery> queries) {
    return map(queries, SearchQueryBuilders::should);
  }

  public static SearchConstantScoreQuery.Builder constantScore() {
    return new SearchConstantScoreQuery.Builder();
  }

  public static SearchConstantScoreQuery constantScore(
      final Function<SearchConstantScoreQuery.Builder, ObjectBuilder<SearchConstantScoreQuery>>
          fn) {
    return fn.apply(constantScore()).build();
  }

  public static SearchQuery constantScore(final SearchQuery query) {
    return constantScore(q -> q.filter(query)).toSearchQuery();
  }

  public static SearchExistsQuery.Builder exists() {
    return new SearchExistsQuery.Builder();
  }

  public static SearchExistsQuery exists(
      final Function<SearchExistsQuery.Builder, ObjectBuilder<SearchExistsQuery>> fn) {
    return fn.apply(exists()).build();
  }

  public static SearchQuery exists(final String field) {
    return exists(q -> q.field(field)).toSearchQuery();
  }

  public static SearchHasChildQuery.Builder hasChild() {
    return new SearchHasChildQuery.Builder();
  }

  public static SearchHasChildQuery hasChild(
      final Function<SearchHasChildQuery.Builder, ObjectBuilder<SearchHasChildQuery>> fn) {
    return fn.apply(hasChild()).build();
  }

  public static SearchIdsQuery.Builder ids() {
    return new SearchIdsQuery.Builder();
  }

  public static SearchIdsQuery ids(
      final Function<SearchIdsQuery.Builder, ObjectBuilder<SearchIdsQuery>> fn) {
    return fn.apply(ids()).build();
  }

  public static SearchQuery ids(final List<String> ids) {
    return ids(q -> q.values(withoutNull(ids))).toSearchQuery();
  }

  public static SearchQuery ids(final Collection<String> ids) {
    return ids(new ArrayList<>(Objects.requireNonNullElse(ids, List.of())));
  }

  public static SearchQuery ids(final String... ids) {
    return ids(List.of(Objects.requireNonNullElse(ids, new String[0])));
  }

  public static SearchMatchQuery.Builder match() {
    return new SearchMatchQuery.Builder();
  }

  public static SearchMatchQuery match(
      final Function<SearchMatchQuery.Builder, ObjectBuilder<SearchMatchQuery>> fn) {
    return fn.apply(match()).build();
  }

  public static <A> SearchQuery match(
      final String field, final String value, final SearchMatchQueryOperator operator) {
    return match((q) -> q.field(field).query(value).operator(operator)).toSearchQuery();
  }

  public static SearchQuery matchAll() {
    return new SearchMatchAllQuery.Builder().build().toSearchQuery();
  }

  public static SearchQuery matchNone() {
    return new SearchMatchNoneQuery.Builder().build().toSearchQuery();
  }

  public static SearchMatchPhraseQuery.Builder matchPhrase() {
    return new SearchMatchPhraseQuery.Builder();
  }

  public static SearchMatchPhraseQuery matchPhrase(
      final Function<SearchMatchPhraseQuery.Builder, ObjectBuilder<SearchMatchPhraseQuery>> fn) {
    return fn.apply(matchPhrase()).build();
  }

  public static <A> SearchQuery matchPhrase(final String field, final String value) {
    return matchPhrase((q) -> q.field(field).query(value)).toSearchQuery();
  }

  public static SearchPrefixQuery.Builder prefix() {
    return new SearchPrefixQuery.Builder();
  }

  public static SearchPrefixQuery prefix(
      final Function<SearchPrefixQuery.Builder, ObjectBuilder<SearchPrefixQuery>> fn) {
    return fn.apply(prefix()).build();
  }

  public static SearchQuery prefix(final String field, final String value) {
    return prefix(q -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery.Builder query() {
    return new SearchQuery.Builder();
  }

  public static SearchQuery query(
      final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
    return fn.apply(query()).build();
  }

  public static SearchRangeQuery.Builder range() {
    return new SearchRangeQuery.Builder();
  }

  public static SearchRangeQuery range(
      final Function<SearchRangeQuery.Builder, ObjectBuilder<SearchRangeQuery>> fn) {
    return fn.apply(range()).build();
  }

  public static <A> SearchQuery gt(final String field, final A gt) {
    return SearchRangeQuery.of(q -> q.field(field).gt(gt)).toSearchQuery();
  }

  public static <A> SearchQuery gte(final String field, final A gte) {
    return SearchRangeQuery.of(q -> q.field(field).gte(gte)).toSearchQuery();
  }

  public static <A> SearchQuery lt(final String field, final A lt) {
    return SearchRangeQuery.of(q -> q.field(field).lt(lt)).toSearchQuery();
  }

  public static <A> SearchQuery lte(final String field, final A lte) {
    return SearchRangeQuery.of(q -> q.field(field).lte(lte)).toSearchQuery();
  }

  public static <A> SearchQuery gteLte(final String field, final A gte, final A lte) {
    return SearchRangeQuery.of(q -> q.field(field).gte(gte).lte(lte)).toSearchQuery();
  }

  public static <A> SearchQuery gtLte(final String field, final A gt, final A lte) {
    return SearchRangeQuery.of(q -> q.field(field).gt(gt).lte(lte)).toSearchQuery();
  }

  public static SearchQuery hasChildQuery(final String type, final SearchQuery query) {
    return hasChild(q -> q.query(query).type(type)).toSearchQuery();
  }

  public static SearchTermQuery.Builder term() {
    return new SearchTermQuery.Builder();
  }

  public static SearchTermQuery term(
      final Function<SearchTermQuery.Builder, ObjectBuilder<SearchTermQuery>> fn) {
    return fn.apply(term()).build();
  }

  public static SearchQuery term(final String field, final Short value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final Integer value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final Long value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final Double value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final String value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final boolean value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final TypedValue value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchTermsQuery.Builder terms() {
    return new SearchTermsQuery.Builder();
  }

  public static SearchTermsQuery terms(
      final Function<SearchTermsQuery.Builder, ObjectBuilder<SearchTermsQuery>> fn) {
    return fn.apply(terms()).build();
  }

  public static <C extends Collection<Integer>> SearchQuery intTerms(
      final String field, final C values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.getFirst());
    } else {
      return SearchTermsQuery.of(q -> q.field(field).intTerms(fieldValues)).toSearchQuery();
    }
  }

  public static <C extends Collection<Long>> SearchQuery longTerms(
      final String field, final C values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.getFirst());
    } else {
      return SearchTermsQuery.of(q -> q.field(field).longTerms(fieldValues)).toSearchQuery();
    }
  }

  public static SearchQuery stringTerms(final String field, final Collection<String> values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.getFirst());
    } else {
      return SearchTermsQuery.of(q -> q.field(field).stringTerms(fieldValues)).toSearchQuery();
    }
  }

  public static SearchQuery objectTerms(final String field, final Collection<Object> values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, TypedValue.toTypedValue(fieldValues.getFirst()));
    } else {
      final var typedValues =
          fieldValues.stream().map(TypedValue::toTypedValue).collect(Collectors.toList());
      return SearchTermsQuery.of(q -> q.field(field).terms(typedValues)).toSearchQuery();
    }
  }

  private static IllegalStateException unexpectedOperation(final String type, final Operator op) {
    return new IllegalStateException("Unexpected %s operation: %s".formatted(type, op));
  }

  public static <C extends List<Operation<Integer>>> List<SearchQuery> intOperations(
      final String field, final C operations) {
    if (operations == null || operations.isEmpty()) {
      return List.of();
    } else {
      final var queries = new ArrayList<SearchQuery>();
      SearchRangeQuery.Builder rangeQueryBuilder = null;

      for (final Operation<Integer> op : operations) {
        switch (op.operator()) {
          case EQUALS -> queries.add(term(field, op.value()));
          case NOT_EQUALS -> queries.add(mustNot(term(field, op.value())));
          case EXISTS -> queries.add(exists(field));
          case NOT_EXISTS -> queries.add(mustNot(exists(field)));
          case GREATER_THAN ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.gt(op.value()));
          case GREATER_THAN_EQUALS ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.gte(op.value()));
          case LOWER_THAN ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.lt(op.value()));
          case LOWER_THAN_EQUALS ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.lte(op.value()));
          case IN -> queries.add(intTerms(field, op.values()));
          case NOT_IN -> queries.add(mustNot(intTerms(field, op.values())));
          default -> throw unexpectedOperation("Integer", op.operator());
        }
      }

      // Add the consolidated range query if any range operations were processed
      if (rangeQueryBuilder != null) {
        queries.add(rangeQueryBuilder.build().toSearchQuery());
      }

      return queries;
    }
  }

  public static <C extends List<Operation<Long>>> List<SearchQuery> longOperations(
      final String field, final C operations) {
    if (operations == null || operations.isEmpty()) {
      return List.of();
    } else {
      final var queries = new ArrayList<SearchQuery>();
      SearchRangeQuery.Builder rangeQueryBuilder = null;

      for (final Operation<Long> op : operations) {
        switch (op.operator()) {
          case EQUALS -> queries.add(term(field, op.value()));
          case NOT_EQUALS -> queries.add(mustNot(term(field, op.value())));
          case EXISTS -> queries.add(exists(field));
          case NOT_EXISTS -> queries.add(mustNot(exists(field)));
          case GREATER_THAN ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.gt(op.value()));
          case GREATER_THAN_EQUALS ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.gte(op.value()));
          case LOWER_THAN ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.lt(op.value()));
          case LOWER_THAN_EQUALS ->
              rangeQueryBuilder = buildRangeQuery(rangeQueryBuilder, field, b -> b.lte(op.value()));
          case IN -> queries.add(longTerms(field, op.values()));
          case NOT_IN -> queries.add(mustNot(longTerms(field, op.values())));
          default -> throw unexpectedOperation("Long", op.operator());
        }
      }

      // Add the consolidated range query if any range operations were processed
      if (rangeQueryBuilder != null) {
        queries.add(rangeQueryBuilder.build().toSearchQuery());
      }

      return queries;
    }
  }

  public static <C extends List<Operation<String>>> List<SearchQuery> stringOperations(
      final String field, final C operations) {
    if (operations == null || operations.isEmpty()) {
      return List.of();
    } else {
      final var searchQueries = new ArrayList<SearchQuery>();
      operations.forEach(
          op -> {
            searchQueries.add(
                switch (op.operator()) {
                  case EQUALS -> term(field, op.value());
                  case NOT_EQUALS -> mustNot(term(field, op.value()));
                  case EXISTS -> exists(field);
                  case NOT_EXISTS -> mustNot(exists(field));
                  case IN -> stringTerms(field, op.values());
                  case NOT_IN -> mustNot(stringTerms(field, op.values()));
                  case LIKE -> wildcardQuery(field, op.value());
                  default -> throw unexpectedOperation("String", op.operator());
                });
          });
      return searchQueries;
    }
  }

  public static <C extends List<Operation<String>>> List<SearchQuery> stringMatchPhraseOperations(
      final String field, final C operations) {

    if (operations == null || operations.isEmpty()) {
      return List.of();
    }

    return operations.stream()
        .map(
            op ->
                switch (op.operator()) {
                  case EQUALS -> matchPhrase(field, op.value());
                  case NOT_EQUALS -> mustNot(matchPhrase(field, op.value()));
                  case EXISTS -> exists(field);
                  case NOT_EXISTS -> mustNot(exists(field));
                  case IN ->
                      or(op.values().stream().map(value -> matchPhrase(field, value)).toList());
                  case LIKE ->
                      wildcardQuery(field, Objects.requireNonNull(op.value()).toLowerCase());
                  default -> throw unexpectedOperation("String", op.operator());
                })
        .toList();
  }

  public static <C extends List<Operation<String>>>
      List<SearchQuery> stringMatchPhraseWithHasChildOperations(
          final String field, final C operations, final String childType) {

    if (operations == null || operations.isEmpty()) {
      return List.of();
    }

    return operations.stream()
        .map(
            op ->
                switch (op.operator()) {
                  case EQUALS -> hasChildQuery(childType, matchPhrase(field, op.value()));

                  case NOT_EQUALS ->
                      hasChildQuery(
                          childType,
                          bool(b ->
                                  b.must(List.of(exists(field)))
                                      .mustNot(List.of(matchPhrase(field, op.value()))))
                              .toSearchQuery());

                  case EXISTS ->
                      hasChildQuery(
                          childType, bool(b -> b.must(List.of(exists(field)))).toSearchQuery());

                  case NOT_EXISTS ->
                      hasChildQuery(
                          childType,
                          bool(b -> b.must(List.of(exists(field))).mustNot(List.of(exists(field))))
                              .toSearchQuery());

                  case IN ->
                      hasChildQuery(
                          childType,
                          or(
                              op.values().stream()
                                  .map(value -> matchPhrase(field, value))
                                  .toList()));

                  case LIKE ->
                      hasChildQuery(
                          childType,
                          wildcardQuery(field, Objects.requireNonNull(op.value()).toLowerCase()));

                  default -> throw unexpectedOperation("String", op.operator());
                })
        .toList();
  }

  private static String formatDate(final OffsetDateTime dateTime) {
    return DATE_TIME_FORMATTER.format(dateTime);
  }

  private static SearchRangeQuery.Builder buildRangeQuery(
      SearchRangeQuery.Builder builder,
      final String field,
      final Consumer<SearchRangeQuery.Builder> builderConsumer) {
    if (builder == null) {
      builder = new SearchRangeQuery.Builder().field(field).format(DATE_TIME_FORMAT);
    }
    builderConsumer.accept(builder);
    return builder;
  }

  public static <C extends List<Operation<OffsetDateTime>>> List<SearchQuery> dateTimeOperations(
      final String field, final C operations) {
    if (operations == null || operations.isEmpty()) {
      return List.of();
    } else {
      final var queries = new ArrayList<SearchQuery>();
      SearchRangeQuery.Builder rangeQueryBuilder = null;
      for (final Operation<OffsetDateTime> op : operations) {
        switch (op.operator()) {
          case EQUALS -> queries.add(term(field, formatDate(op.value())));
          case NOT_EQUALS -> queries.add(mustNot(term(field, formatDate(op.value()))));
          case EXISTS -> queries.add(exists(field));
          case NOT_EXISTS -> queries.add(mustNot(exists(field)));
          case GREATER_THAN ->
              rangeQueryBuilder =
                  buildRangeQuery(rangeQueryBuilder, field, b -> b.gt(formatDate(op.value())));
          case GREATER_THAN_EQUALS ->
              rangeQueryBuilder =
                  buildRangeQuery(rangeQueryBuilder, field, b -> b.gte(formatDate(op.value())));
          case LOWER_THAN ->
              rangeQueryBuilder =
                  buildRangeQuery(rangeQueryBuilder, field, b -> b.lt(formatDate(op.value())));
          case LOWER_THAN_EQUALS ->
              rangeQueryBuilder =
                  buildRangeQuery(rangeQueryBuilder, field, b -> b.lte(formatDate(op.value())));
          default -> throw unexpectedOperation("Date", op.operator());
        }
      }
      if (rangeQueryBuilder != null) {
        queries.add(rangeQueryBuilder.build().toSearchQuery());
      }
      return queries;
    }
  }

  public static <C extends UntypedOperation> SearchQuery variableOperation(
      final String field, final C operation) {
    // Handle common operations
    final var res =
        switch (operation.operator()) {
          case EQUALS -> term(field, TypedValue.toTypedValue(operation.value()));
          case NOT_EQUALS -> mustNot(term(field, TypedValue.toTypedValue(operation.value())));
          case EXISTS -> exists(field);
          case NOT_EXISTS -> mustNot(exists(field));
          case IN -> objectTerms(field, operation.values());
          default -> null;
        };
    if (res != null) {
      return res;
    }

    // Consolidate range operations for numeric types
    final var type = operation.type();
    if (type.equals(ValueTypeEnum.LONG) || type.equals(ValueTypeEnum.DOUBLE)) {
      SearchRangeQuery.Builder rangeQueryBuilder = null;

      switch (operation.operator()) {
        case GREATER_THAN ->
            rangeQueryBuilder =
                buildRangeQuery(rangeQueryBuilder, field, b -> b.gt(operation.value()));
        case GREATER_THAN_EQUALS ->
            rangeQueryBuilder =
                buildRangeQuery(rangeQueryBuilder, field, b -> b.gte(operation.value()));
        case LOWER_THAN ->
            rangeQueryBuilder =
                buildRangeQuery(rangeQueryBuilder, field, b -> b.lt(operation.value()));
        case LOWER_THAN_EQUALS ->
            rangeQueryBuilder =
                buildRangeQuery(rangeQueryBuilder, field, b -> b.lte(operation.value()));
        default -> throw unexpectedOperation("Variable (numeric)", operation.operator());
      }

      if (rangeQueryBuilder != null) {
        return rangeQueryBuilder.build().toSearchQuery();
      }
    }

    // Handle string-specific operations
    if (type.equals(ValueTypeEnum.STRING)) {
      return switch (operation.operator()) {
        case LIKE -> wildcardQuery(field, (String) operation.value());
        default -> throw unexpectedOperation("Variable (string)", operation.operator());
      };
    }

    return null;
  }

  public static <C extends List<UntypedOperation>> List<SearchQuery> variableOperations(
      final String field, final C operations) {
    if (operations == null || operations.isEmpty()) {
      return List.of();
    } else {
      return operations.stream()
          .map(op -> variableOperation(field, op))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }
  }

  public static SearchWildcardQuery.Builder wildcard() {
    return new SearchWildcardQuery.Builder();
  }

  public static SearchWildcardQuery wildcard(
      final Function<SearchWildcardQuery.Builder, ObjectBuilder<SearchWildcardQuery>> fn) {
    return fn.apply(wildcard()).build();
  }

  public static SearchQuery wildcardQuery(final String field, final String value) {
    return wildcard(q -> q.field(field).value(value)).toSearchQuery();
  }

  public static Builder hasParent() {
    return new Builder();
  }

  public static SearchHasParentQuery hasParent(
      final Function<Builder, ObjectBuilder<SearchHasParentQuery>> fn) {
    return fn.apply(hasParent()).build();
  }

  public static SearchQuery hasParentQuery(final String parent, final SearchQuery query) {
    return hasParent(q -> q.parentType(parent).query(query)).toSearchQuery();
  }
}
