/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.util.DecisionVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getValueSearchField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableStringValueField;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.filter.util.DateFilterQueryUtilES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil;
import io.camunda.optimize.service.util.DecisionVariableHelper;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@RequiredArgsConstructor
public abstract class DecisionVariableQueryFilterES extends AbstractVariableQueryFilterES
    implements QueryFilterES<VariableFilterDataDto<?>> {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  abstract String getVariablePath();

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<VariableFilterDataDto<?>> variableFilters,
      final FilterContext filterContext) {
    if (variableFilters != null) {
      query.filter(
          variableFilters.stream()
              .map(
                  variable ->
                      createFilterQueryBuilder(variable, filterContext.getTimezone()).build())
              .toList());
    }
  }

  private Query.Builder createFilterQueryBuilder(
      final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    Query.Builder queryBuilder = new Query.Builder();
    queryBuilder.matchAll(a -> a);

    switch (dto.getType()) {
      case BOOLEAN:
        final BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder = createBooleanQueryBuilder(booleanVarDto);
        break;
      case STRING:
        final StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        queryBuilder = createStringQueryBuilder(stringVarDto);
        break;
      case INTEGER:
      case DOUBLE:
      case SHORT:
      case LONG:
        final OperatorMultipleValuesVariableFilterDataDto numericVarDto =
            (OperatorMultipleValuesVariableFilterDataDto) dto;
        queryBuilder = createNumericQueryBuilder(numericVarDto);
        break;
      case DATE:
        final DateVariableFilterDataDto dateVarDto = (DateVariableFilterDataDto) dto;
        queryBuilder = createDateQueryBuilder(dateVarDto, timezone);
        break;
      default:
        logger.warn(
            "Could not filter for variables! Type [{}] is not supported for variable filters. Ignoring filter.",
            dto.getType());
    }
    return queryBuilder;
  }

  @Override
  protected Query.Builder createContainsOneOfTheGivenStringsQueryBuilder(
      final StringVariableFilterDataDto dto) {
    final Query.Builder containOneOfTheGivenStrings =
        createContainsOneOfTheGivenStringsQueryBuilder(dto.getName(), dto.getData().getValues());

    if (NOT_CONTAINS.equals(dto.getData().getOperator())) {
      final Query.Builder builder = new Query.Builder();
      builder.bool(b -> b.mustNot(containOneOfTheGivenStrings.build()));
      return builder;
    } else {
      return containOneOfTheGivenStrings;
    }
  }

  @Override
  protected Query.Builder createContainsOneOfTheGivenStringsQueryBuilder(
      final String variableId, final List<String> values) {

    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> {
          values.stream()
              .filter(Objects::nonNull)
              .forEach(
                  stringVal ->
                      b.should(createContainsGivenStringQuery(variableId, stringVal).build()));

          final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
          if (hasNullValues) {
            b.should(createFilterForUndefinedOrNullQueryBuilder(variableId).build());
          }
          return b;
        });

    return builder;
  }

  @Override
  protected Query.Builder createContainsGivenStringQuery(
      final String variableId, final String valueToContain) {

    final Query.Builder builder = new Query.Builder();

    final BoolQuery.Builder containsVariableString = new BoolQuery.Builder();
    containsVariableString.must(m -> m.term(t -> t.field(getVariableIdField()).value(variableId)));

    final String lowerCaseValue = valueToContain.toLowerCase(Locale.ENGLISH);
    final Query filter =
        (lowerCaseValue.length() > MAX_GRAM)
            /*
              using the slow wildcard query for uncommonly large filter strings (> 10 chars)
            */
            ? Query.of(
                q ->
                    q.wildcard(
                        w ->
                            w.field(getValueSearchField(getVariablePath(), LOWERCASE_FIELD))
                                .wildcard(buildWildcardQuery(lowerCaseValue))))
            /*
              using Elasticsearch ngrams to filter for strings < 10 chars,
              because it's fast but increasing the number of chars makes the index bigger
            */
            : Query.of(
                q ->
                    q.term(
                        t ->
                            t.field(getValueSearchField(getVariablePath(), N_GRAM_FIELD))
                                .value(lowerCaseValue)));

    containsVariableString.must(filter);

    builder.nested(
        n ->
            n.path(getVariablePath())
                .query(q -> q.bool(containsVariableString.build()))
                .scoreMode(ChildScoreMode.None));
    return builder;
  }

  @Override
  protected Query.Builder createEqualsOneOrMoreValuesQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    final Query.Builder variableFilterBuilder =
        createMultiValueVariableFilterQuery(
            getVariableId(dto), dto.getType(), dto.getData().getValues());

    if (NOT_IN.equals(dto.getData().getOperator())) {
      final Query.Builder builder = new Query.Builder();
      builder.bool(b -> b.mustNot(variableFilterBuilder.build()));
      return builder;
    } else {
      return variableFilterBuilder;
    }
  }

  @Override
  protected Query.Builder createBooleanQueryBuilder(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createMultiValueVariableFilterQuery(
        getVariableId(dto), dto.getType(), dto.getData().getValues());
  }

  @Override
  protected Query.Builder createNumericQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto(dto);

    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();

    final Query.Builder builder = new Query.Builder();
    final Supplier<BoolQuery.Builder> supplier =
        () ->
            new BoolQuery.Builder()
                .must(m -> m.term(t -> t.field(getVariableIdField()).value(getVariableId(dto))));
    if (data.getValues().isEmpty()) {
      logger.warn(
          "Could not filter for variables! No values provided for operator [{}] and type [{}]. Ignoring filter.",
          data.getOperator(),
          dto.getType());
      builder.bool(supplier.get().build());
      return builder;
    }

    final Object value = OperatorMultipleValuesVariableFilterDataDtoUtil.retrieveValue(dto);
    switch (data.getOperator()) {
      case IN:
      case NOT_IN:
        return createEqualsOneOrMoreValuesQueryBuilder(dto);
      case LESS_THAN:
        builder.nested(
            n ->
                n.path(getVariablePath())
                    .scoreMode(ChildScoreMode.None)
                    .query(
                        q ->
                            q.bool(
                                supplier
                                    .get()
                                    .must(
                                        m ->
                                            m.range(
                                                r ->
                                                    r.date(
                                                        d ->
                                                            d.field(nestedVariableValueFieldLabel)
                                                                .lt(String.valueOf(value)))))
                                    .build())));
        break;
      case GREATER_THAN:
        builder.nested(
            n ->
                n.path(getVariablePath())
                    .scoreMode(ChildScoreMode.None)
                    .query(
                        q ->
                            q.bool(
                                supplier
                                    .get()
                                    .must(
                                        m ->
                                            m.range(
                                                r ->
                                                    r.date(
                                                        d ->
                                                            d.field(nestedVariableValueFieldLabel)
                                                                .gt(String.valueOf(value)))))
                                    .build())));
        break;
      case LESS_THAN_EQUALS:
        builder.nested(
            n ->
                n.path(getVariablePath())
                    .scoreMode(ChildScoreMode.None)
                    .query(
                        q ->
                            q.bool(
                                supplier
                                    .get()
                                    .must(
                                        m ->
                                            m.range(
                                                r ->
                                                    r.date(
                                                        d ->
                                                            d.field(nestedVariableValueFieldLabel)
                                                                .lte(String.valueOf(value)))))
                                    .build())));
        break;
      case GREATER_THAN_EQUALS:
        builder.nested(
            n ->
                n.path(getVariablePath())
                    .scoreMode(ChildScoreMode.None)
                    .query(
                        q ->
                            q.bool(
                                supplier
                                    .get()
                                    .must(
                                        m ->
                                            m.range(
                                                r ->
                                                    r.date(
                                                        d ->
                                                            d.field(nestedVariableValueFieldLabel)
                                                                .gte(String.valueOf(value)))))
                                    .build())));
        break;
      default:
        builder.nested(
            n ->
                n.path(getVariablePath())
                    .scoreMode(ChildScoreMode.None)
                    .query(q -> q.bool(supplier.get().build())));
        logger.warn(
            "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
            data.getOperator(),
            dto.getType());
    }
    return builder;
  }

  @Override
  protected Query.Builder createDateQueryBuilder(
      final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> {
          b.minimumShouldMatch("1");
          if (dto.getData().isIncludeUndefined()) {
            b.should(createFilterForUndefinedOrNullQueryBuilder(getVariableId(dto)).build());
          } else if (dto.getData().isExcludeUndefined()) {
            b.should(createExcludeUndefinedOrNullQueryBuilder(getVariableId(dto)).build());
          }

          final BoolQuery.Builder dateValueFilterQuery =
              new BoolQuery.Builder()
                  .must(m -> m.term(t -> t.field(getVariableIdField()).value(getVariableId(dto))));
          DateFilterQueryUtilES.addFilters(
              dateValueFilterQuery,
              Collections.singletonList(dto.getData()),
              getVariableValueFieldForType(dto.getType()),
              timezone);
          final BoolQuery build = dateValueFilterQuery.build();
          if (!build.filter().isEmpty()) {
            b.should(
                s ->
                    s.nested(
                        n ->
                            n.path(getVariablePath())
                                .query(q -> q.bool(build))
                                .scoreMode(ChildScoreMode.None)));
          }
          return b;
        });

    return builder;
  }

  private Query.Builder createMultiValueVariableFilterQuery(
      final String variableId, final VariableType variableType, final List<?> values) {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> {
          b.minimumShouldMatch("1");
          final String nestedVariableIdFieldLabel = getVariableIdField();
          final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);

          final List<FieldValue> nonNullValues =
              values.stream()
                  .filter(Objects::nonNull)
                  .map(FieldValue::of)
                  .collect(Collectors.toList());

          if (!nonNullValues.isEmpty()) {
            b.should(
                s ->
                    s.nested(
                        n ->
                            n.path(getVariablePath())
                                .query(
                                    q ->
                                        q.bool(
                                            bb ->
                                                bb.must(
                                                        m ->
                                                            m.term(
                                                                t ->
                                                                    t.field(
                                                                            nestedVariableIdFieldLabel)
                                                                        .value(variableId)))
                                                    .must(
                                                        m ->
                                                            m.terms(
                                                                t ->
                                                                    t.field(
                                                                            nestedVariableValueFieldLabel)
                                                                        .terms(
                                                                            tt ->
                                                                                tt.value(
                                                                                    nonNullValues))))))
                                .scoreMode(ChildScoreMode.None)));
          }

          if (nonNullValues.size() < values.size()) {
            b.should(createFilterForUndefinedOrNullQueryBuilder(variableId).build());
          }
          return b;
        });

    return builder;
  }

  private Query.Builder createFilterForUndefinedOrNullQueryBuilder(final String variableId) {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b ->
            b.should(
                    s ->
                        s.bool(
                            bb ->
                                bb.mustNot(
                                    m ->
                                        m.nested(
                                            n ->
                                                n.path(getVariablePath())
                                                    .query(
                                                        q ->
                                                            q.term(
                                                                t ->
                                                                    t.field(getVariableIdField())
                                                                        .value(variableId)))
                                                    .scoreMode(ChildScoreMode.None)))))
                .should(
                    s ->
                        s.bool(
                            bb ->
                                bb.must(
                                    m ->
                                        m.nested(
                                            n ->
                                                n.path(getVariablePath())
                                                    .query(
                                                        q ->
                                                            q.bool(
                                                                bbb ->
                                                                    bbb.must(
                                                                            mm ->
                                                                                mm.term(
                                                                                    t ->
                                                                                        t.field(
                                                                                                getVariableIdField())
                                                                                            .value(
                                                                                                variableId)))
                                                                        .mustNot(
                                                                            mn ->
                                                                                mn.exists(
                                                                                    e ->
                                                                                        e.field(
                                                                                            getVariableStringValueField(
                                                                                                getVariablePath()))))))
                                                    .scoreMode(ChildScoreMode.None)))))
                .minimumShouldMatch("1"));
    return builder;
  }

  private Query.Builder createExcludeUndefinedOrNullQueryBuilder(final String variableId) {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b ->
            b.must(
                m ->
                    m.nested(
                        n ->
                            n.path(getVariablePath())
                                .query(
                                    q ->
                                        q.bool(
                                            bb ->
                                                bb.must(
                                                        mm ->
                                                            mm.term(
                                                                t ->
                                                                    t.field(getVariableIdField())
                                                                        .value(variableId)))
                                                    .must(
                                                        mm ->
                                                            mm.exists(
                                                                e ->
                                                                    e.field(
                                                                        getVariableStringValueField(
                                                                            getVariablePath()))))))
                                .scoreMode(ChildScoreMode.None))));
    return builder;
  }

  private String getVariableId(final VariableFilterDataDto<?> dto) {
    // the input/output variable id is stored as name as we use the same dto's as for process
    // filters here
    // with https://jira.camunda.com/browse/OPT-1942 we intend to introduce a dedicated dto to make
    // the difference clear
    return dto.getName();
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return DecisionVariableHelper.getVariableValueFieldForType(getVariablePath(), type);
  }

  private String getVariableIdField() {
    return DecisionVariableHelper.getVariableClauseIdField(getVariablePath());
  }
}
