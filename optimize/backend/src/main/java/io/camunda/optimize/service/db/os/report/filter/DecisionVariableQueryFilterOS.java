/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil.retrieveValue;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.gt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.gte;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lte;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.wildcardQuery;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.util.DecisionVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getValueSearchField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableStringValueField;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.report.filter.util.DateFilterQueryUtilOS;
import io.camunda.optimize.service.util.DecisionVariableHelper;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DecisionVariableQueryFilterOS extends AbstractVariableQueryFilterOS
    implements QueryFilterOS<VariableFilterDataDto<?>> {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionVariableQueryFilterOS.class);
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  public DecisionVariableQueryFilterOS() {}

  abstract String getVariablePath();

  @Override
  public List<Query> filterQueries(
      final List<VariableFilterDataDto<?>> variableFilters, final FilterContext filterContext) {
    return variableFilters == null
        ? List.of()
        : variableFilters.stream()
            .map(variable -> createFilterQuery(variable, filterContext.getTimezone()))
            .toList();
  }

  private Query createFilterQuery(final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());
    switch (dto.getType()) {
      case BOOLEAN -> {
        final BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        return createBooleanQuery(booleanVarDto);
      }
      case STRING -> {
        final StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        return createStringQuery(stringVarDto);
      }
      case INTEGER, DOUBLE, SHORT, LONG -> {
        final OperatorMultipleValuesVariableFilterDataDto numericVarDto =
            (OperatorMultipleValuesVariableFilterDataDto) dto;
        return createNumericQuery(numericVarDto);
      }
      case DATE -> {
        final DateVariableFilterDataDto dateVarDto = (DateVariableFilterDataDto) dto;
        return createDateQuery(dateVarDto, timezone);
      }
      default ->
          logger.warn(
              "Could not filter for variables! Type [{}] is not supported for variable filters. Ignoring filter.",
              dto.getType());
    }
    return matchAll();
  }

  @Override
  protected Query createContainsOneOfTheGivenStringsQuery(final StringVariableFilterDataDto dto) {
    final Query containOneOfTheGivenStrings =
        createContainsOneOfTheGivenStringsQuery(dto.getName(), dto.getData().getValues());

    if (NOT_CONTAINS.equals(dto.getData().getOperator())) {
      return not(containOneOfTheGivenStrings);
    } else {
      return containOneOfTheGivenStrings;
    }
  }

  @Override
  protected Query createContainsOneOfTheGivenStringsQuery(
      final String variableId, final List<String> values) {
    final BoolQuery.Builder variableFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");

    values.stream()
        .filter(Objects::nonNull)
        .forEach(
            stringVal ->
                variableFilterBuilder.should(
                    createContainsGivenStringQuery(variableId, stringVal)));

    final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
    if (hasNullValues) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQuery(variableId));
    }

    return variableFilterBuilder.build().toQuery();
  }

  @Override
  protected Query createContainsGivenStringQuery(
      final String variableId, final String valueToContain) {
    final String lowerCaseValue = valueToContain.toLowerCase();
    final Query filter =
        (lowerCaseValue.length() > MAX_GRAM)
            /*
              using the slow wildcard query for uncommonly large filter strings (> 10 chars)
            */
            ? wildcardQuery(
                getValueSearchField(getVariablePath(), LOWERCASE_FIELD),
                buildWildcardQuery(lowerCaseValue))
            /*
              using Opensearch ngrams to filter for strings < 10 chars,
              because it's fast but increasing the number of chars makes the index bigger
            */
            : term(getValueSearchField(getVariablePath(), N_GRAM_FIELD), lowerCaseValue);
    final Query containsVariableString = and(filter, term(getVariableIdField(), variableId));
    return nested(getVariablePath(), containsVariableString, ChildScoreMode.None);
  }

  @Override
  protected Query createEqualsOneOrMoreValuesQuery(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    final Query variableFilterBuilder =
        createMultiValueVariableFilterQuery(
            getVariableId(dto), dto.getType(), dto.getData().getValues(), QueryDSL::stringTerms);

    if (NOT_IN.equals(dto.getData().getOperator())) {
      return not(variableFilterBuilder);
    } else {
      return variableFilterBuilder;
    }
  }

  @Override
  protected Query createBooleanQuery(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createMultiValueVariableFilterQuery(
        getVariableId(dto), dto.getType(), dto.getData().getValues(), QueryDSL::boolTerms);
  }

  private <A> Query createMultiValueVariableFilterQuery(
      final String variableId,
      final VariableType variableType,
      final List<A> values,
      final BiFunction<String, List<A>, Query> termsQuery) {
    final BoolQuery.Builder variableFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");
    final String nestedVariableIdFieldLabel = getVariableIdField();
    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);
    final List<A> nonNullValues = values.stream().filter(Objects::nonNull).toList();

    if (!nonNullValues.isEmpty()) {
      variableFilterBuilder.should(
          nested(
              getVariablePath(),
              and(
                  term(nestedVariableIdFieldLabel, variableId),
                  termsQuery.apply(nestedVariableValueFieldLabel, nonNullValues)),
              ChildScoreMode.None));
    }

    if (nonNullValues.size() < values.size()) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQuery(variableId));
    }
    return variableFilterBuilder.build().toQuery();
  }

  @Override
  protected Query createNumericQuery(final OperatorMultipleValuesVariableFilterDataDto dto) {
    OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto(dto);

    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();
    final Query basicQuery = term(getVariableIdField(), getVariableId(dto));

    if (data.getValues().isEmpty()) {
      logger.warn(
          "Could not filter for variables! No values provided for operator [{}] and type [{}]. Ignoring filter.",
          data.getOperator(),
          dto.getType());
      return basicQuery;
    }

    final Function<Query, Query> nestedAnd =
        (query) -> nested(getVariablePath(), and(query, basicQuery), ChildScoreMode.None);

    final Object value = retrieveValue(dto);
    return switch (data.getOperator()) {
      case IN, NOT_IN -> createEqualsOneOrMoreValuesQuery(dto);
      case LESS_THAN -> nestedAnd.apply(lt(nestedVariableValueFieldLabel, value));
      case GREATER_THAN -> nestedAnd.apply(gt(nestedVariableValueFieldLabel, value));
      case LESS_THAN_EQUALS -> nestedAnd.apply(lte(nestedVariableValueFieldLabel, value));
      case GREATER_THAN_EQUALS -> nestedAnd.apply(gte(nestedVariableValueFieldLabel, value));
      default -> {
        logger.warn(
            "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
            data.getOperator(),
            dto.getType());
        yield nested(getVariablePath(), basicQuery, ChildScoreMode.None);
      }
    };
  }

  @Override
  protected Query createDateQuery(final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final BoolQuery.Builder dateFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");

    if (dto.getData().isIncludeUndefined()) {
      dateFilterBuilder.should(createFilterForUndefinedOrNullQuery(getVariableId(dto)));
    } else if (dto.getData().isExcludeUndefined()) {
      dateFilterBuilder.should(createExcludeUndefinedOrNullQuery(getVariableId(dto)));
    }

    final List<Query> rangeQueries =
        DateFilterQueryUtilOS.createRangeQueries(
            Collections.singletonList(dto.getData()),
            getVariableValueFieldForType(dto.getType()),
            timezone);
    if (!rangeQueries.isEmpty()) {
      final Query dateValueFilterQuery =
          new BoolQuery.Builder()
              .must(term(getVariableIdField(), getVariableId(dto)))
              .must(rangeQueries)
              .build()
              .toQuery();
      dateFilterBuilder.should(
          nested(getVariablePath(), dateValueFilterQuery, ChildScoreMode.None));
    }

    return dateFilterBuilder.build().toQuery();
  }

  private Query createFilterForUndefinedOrNullQuery(final String variableId) {
    return new BoolQuery.Builder()
        .should(
            // undefined
            not(
                nested(
                    getVariablePath(),
                    term(getVariableIdField(), variableId),
                    ChildScoreMode.None)))
        .should(
            // or null value
            nested(
                getVariablePath(),
                new BoolQuery.Builder()
                    .must(term(getVariableIdField(), variableId))
                    .mustNot(exists(getVariableStringValueField(getVariablePath())))
                    .build()
                    .toQuery(),
                ChildScoreMode.None))
        .minimumShouldMatch("1")
        .build()
        .toQuery();
  }

  private Query createExcludeUndefinedOrNullQuery(final String variableId) {
    return nested(
        getVariablePath(),
        and(
            term(getVariableIdField(), variableId),
            exists(getVariableStringValueField(getVariablePath()))),
        ChildScoreMode.None);
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
