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
import static io.camunda.optimize.service.db.es.util.ProcessVariableHelperES.createExcludeUndefinedOrNullQueryFilterBuilder;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getValueSearchField;

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
import io.camunda.optimize.service.db.es.util.ProcessVariableHelperES;
import io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public abstract class AbstractProcessVariableQueryFilterES extends AbstractVariableQueryFilterES {

  protected Query.Builder createFilterQueryBuilder(
      final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    Query.Builder queryBuilder = new Query.Builder();

    switch (dto.getType()) {
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
      case BOOLEAN:
        final BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder = createBooleanQueryBuilder(booleanVarDto);
        break;
      default:
        log.warn(
            "Could not filter for variables! "
                + "Type [{}] is not supported for variable filters. Ignoring filter.",
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
      final String variableName, final List<String> values) {
    final Query.Builder builder = new Query.Builder();
    final BoolQuery.Builder variableFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");

    values.stream()
        .filter(Objects::nonNull)
        .forEach(
            stringVal ->
                variableFilterBuilder.should(
                    createContainsGivenStringQuery(variableName, stringVal).build()));

    final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
    if (hasNullValues) {
      variableFilterBuilder.should(
          s ->
              s.bool(
                  createFilterForUndefinedOrNullQueryBuilder(variableName, VariableType.STRING)
                      .build()));
    }

    builder.bool(variableFilterBuilder.build());
    return builder;
  }

  @Override
  protected Query.Builder createContainsGivenStringQuery(
      final String variableName, final String valueToContain) {
    final Query.Builder queryBuilder = new Query.Builder();
    final BoolQuery.Builder containsVariableString = new BoolQuery.Builder();

    containsVariableString
        .must(m -> m.term(t -> t.field(getNestedVariableNameField()).value(variableName)))
        .must(
            m ->
                m.term(
                    t -> t.field(getNestedVariableTypeField()).value(VariableType.STRING.getId())));

    containsVariableString.must(getBuilder(valueToContain).build());

    queryBuilder.nested(
        n ->
            n.path(VARIABLES)
                .query(q -> q.bool(containsVariableString.build()))
                .scoreMode(ChildScoreMode.None));
    return queryBuilder;
  }

  @Override
  protected Query.Builder createEqualsOneOrMoreValuesQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {

    final Query.Builder variableFilterBuilder =
        createEqualsOneOrMoreValuesFilterQuery(
            dto.getName(), dto.getType(), dto.getData().getValues());

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

    return createEqualsOneOrMoreValuesFilterQuery(
        dto.getName(), dto.getType(), dto.getData().getValues());
  }

  @Override
  protected Query.Builder createNumericQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    final Query.Builder builder = new Query.Builder();
    OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto(dto);

    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();
    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    boolQueryBuilder
        .must(m -> m.term(t -> t.field(getNestedVariableNameField()).value(dto.getName())))
        .must(m -> m.term(t -> t.field(getNestedVariableTypeField()).value(dto.getType().getId())));

    final Object value = OperatorMultipleValuesVariableFilterDataDtoUtil.retrieveValue(dto);
    switch (data.getOperator()) {
      case IN:
      case NOT_IN:
        return createEqualsOneOrMoreValuesQueryBuilder(dto);
      case LESS_THAN:
        builder.nested(
            n ->
                n.path(VARIABLES)
                    .query(
                        qq ->
                            qq.bool(
                                boolQueryBuilder
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
                n.path(VARIABLES)
                    .query(
                        qq ->
                            qq.bool(
                                boolQueryBuilder
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
                n.path(VARIABLES)
                    .query(
                        qq ->
                            qq.bool(
                                boolQueryBuilder
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
                n.path(VARIABLES)
                    .query(
                        qq ->
                            qq.bool(
                                boolQueryBuilder
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
        builder.nested(n -> n.path(VARIABLES).query(qq -> qq.bool(boolQueryBuilder.build())));
        log.warn(
            "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
            data.getOperator(),
            dto.getType());
    }
    return builder;
  }

  @Override
  protected Query.Builder createDateQueryBuilder(
      final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final Query.Builder dateFilterBuilder = new Query.Builder();
    dateFilterBuilder.bool(
        bool -> {
          if (dto.getData().isIncludeUndefined()) {
            bool.should(
                s ->
                    s.bool(
                        createFilterForUndefinedOrNullQueryBuilder(dto.getName(), dto.getType())
                            .build()));
          } else if (dto.getData().isExcludeUndefined()) {
            bool.should(
                s ->
                    s.bool(
                        createExcludeUndefinedOrNullQueryBuilder(dto.getName(), dto.getType())
                            .build()));
          }

          final BoolQuery.Builder dateValueFilterQuery = new BoolQuery.Builder();
          dateValueFilterQuery
              .must(
                  m ->
                      m.terms(
                          t ->
                              t.field(getNestedVariableNameField())
                                  .terms(tt -> tt.value(List.of(FieldValue.of(dto.getName()))))))
              .must(
                  m ->
                      m.term(
                          t -> t.field(getNestedVariableTypeField()).value(dto.getType().getId())));
          DateFilterQueryUtilES.addFilters(
              dateValueFilterQuery,
              Collections.singletonList(dto.getData()),
              getVariableValueFieldForType(dto.getType()),
              timezone);
          final BoolQuery build = dateValueFilterQuery.build();
          if (!build.filter().isEmpty()) {
            bool.should(
                s ->
                    s.nested(
                        n ->
                            n.path(VARIABLES)
                                .query(q -> q.bool(build))
                                .scoreMode(ChildScoreMode.None)));
          }
          return bool;
        });

    return dateFilterBuilder;
  }

  private static Query.Builder getBuilder(final String valueToContain) {
    final String lowerCaseValue = valueToContain.toLowerCase(Locale.ENGLISH);

    final Query.Builder filter = new Query.Builder();
    if (lowerCaseValue.length() > MAX_GRAM) {
      /*
        using the slow wildcard query for uncommonly large filter strings (> 10 chars)
      */
      filter.wildcard(
          w ->
              w.field(getValueSearchField(LOWERCASE_FIELD))
                  .wildcard(buildWildcardQuery(lowerCaseValue)));
    } else {
      /*
        using Elasticsearch ngrams to filter for strings < 10 chars,
        because it's fast but increasing the number of chars makes the index bigger
      */
      filter.term(t -> t.field(getValueSearchField(N_GRAM_FIELD)).value(lowerCaseValue));
    }
    return filter;
  }

  private Query.Builder createEqualsOneOrMoreValuesFilterQuery(
      final String variableName, final VariableType variableType, final List<?> values) {
    final Query.Builder builder = new Query.Builder();
    final BoolQuery.Builder variableFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");
    final String nestedVariableNameFieldLabel = getNestedVariableNameField();
    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);

    final List<?> nonNullValues = values.stream().filter(Objects::nonNull).toList();

    if (!nonNullValues.isEmpty()) {
      variableFilterBuilder.should(
          s ->
              s.nested(
                  n ->
                      n.path(VARIABLES)
                          .query(
                              q ->
                                  q.bool(
                                      b ->
                                          b.must(
                                                  m ->
                                                      m.term(
                                                          t ->
                                                              t.field(nestedVariableNameFieldLabel)
                                                                  .value(variableName)))
                                              .must(
                                                  m ->
                                                      m.term(
                                                          t ->
                                                              t.field(getNestedVariableTypeField())
                                                                  .value(variableType.getId())))
                                              .must(
                                                  m ->
                                                      m.terms(
                                                          t ->
                                                              t.field(nestedVariableValueFieldLabel)
                                                                  .terms(
                                                                      tt ->
                                                                          tt.value(
                                                                              nonNullValues.stream()
                                                                                  .map(
                                                                                      FieldValue
                                                                                          ::of)
                                                                                  .toList()))))))));
    }

    final boolean hasNullValues = nonNullValues.size() < values.size();
    if (hasNullValues) {
      variableFilterBuilder.should(
          s ->
              s.bool(
                  createFilterForUndefinedOrNullQueryBuilder(variableName, variableType).build()));
    }
    builder.bool(variableFilterBuilder.build());
    return builder;
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private BoolQuery.Builder createFilterForUndefinedOrNullQueryBuilder(
      final String variableName, final VariableType variableType) {
    return ProcessVariableHelperES.createFilterForUndefinedOrNullQueryBuilder(
        variableName, variableType);
  }

  private BoolQuery.Builder createExcludeUndefinedOrNullQueryBuilder(
      final String variableName, final VariableType variableType) {
    return createExcludeUndefinedOrNullQueryFilterBuilder(variableName, variableType);
  }
}
