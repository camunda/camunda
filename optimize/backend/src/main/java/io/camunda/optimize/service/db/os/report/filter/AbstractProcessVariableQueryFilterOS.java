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
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.gt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.gte;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lte;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.terms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.wildcardQuery;
import static io.camunda.optimize.service.db.os.util.ProcessVariableHelperOS.createExcludeUndefinedOrNullQueryFilter;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getValueSearchField;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil;
import io.camunda.optimize.service.db.os.report.filter.util.DateFilterQueryUtilOS;
import io.camunda.optimize.service.db.os.util.ProcessVariableHelperOS;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractProcessVariableQueryFilterOS extends AbstractVariableQueryFilterOS {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractProcessVariableQueryFilterOS.class);

  public AbstractProcessVariableQueryFilterOS() {}

  protected Query createFilterQuery(final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    Query query = matchAll();

    switch (dto.getType()) {
      case STRING -> {
        final StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        query = createStringQuery(stringVarDto);
      }
      case INTEGER, DOUBLE, SHORT, LONG -> {
        final OperatorMultipleValuesVariableFilterDataDto numericVarDto =
            (OperatorMultipleValuesVariableFilterDataDto) dto;
        query = createNumericQuery(numericVarDto);
      }
      case DATE -> {
        final DateVariableFilterDataDto dateVarDto = (DateVariableFilterDataDto) dto;
        query = createDateQuery(dateVarDto, timezone);
      }
      case BOOLEAN -> {
        final BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        query = createBooleanQuery(booleanVarDto);
      }
      default ->
          LOG.warn(
              "Could not filter for variables! "
                  + "Type [{}] is not supported for variable filters. Ignoring filter.",
              dto.getType());
    }
    return query;
  }

  @Override
  protected Query createContainsOneOfTheGivenStringsQuery(final StringVariableFilterDataDto dto) {
    final Query containOneOfTheGivenStrings =
        createContainsOneOfTheGivenStringsQuery(dto.getName(), dto.getData().getValues());

    return NOT_CONTAINS.equals(dto.getData().getOperator())
        ? not(containOneOfTheGivenStrings)
        : containOneOfTheGivenStrings;
  }

  @Override
  protected Query createContainsOneOfTheGivenStringsQuery(
      final String variableName, final List<String> values) {
    final BoolQuery.Builder variableFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");

    values.stream()
        .filter(Objects::nonNull)
        .forEach(
            stringVal ->
                variableFilterBuilder.should(
                    createContainsGivenStringQuery(variableName, stringVal)));

    final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
    if (hasNullValues) {
      variableFilterBuilder.should(
          createFilterForUndefinedOrNullQuery(variableName, VariableType.STRING));
    }

    return variableFilterBuilder.build().toQuery();
  }

  @Override
  protected Query createContainsGivenStringQuery(
      final String variableName, final String valueToContain) {
    final String lowerCaseValue = valueToContain.toLowerCase(Locale.ENGLISH);
    final Query filter =
        (lowerCaseValue.length() > MAX_GRAM)
            /*
              using the slow wildcard query for uncommonly large filter strings (> 10 chars)
            */
            ? wildcardQuery(
                getValueSearchField(LOWERCASE_FIELD), buildWildcardQuery(lowerCaseValue))
            /*
              using ngrams to filter for strings < 10 chars,
              because it's fast but increasing the number of chars makes the index bigger
            */
            : term(getValueSearchField(N_GRAM_FIELD), lowerCaseValue);
    final Query containsVariableString =
        and(
            term(getNestedVariableNameField(), variableName),
            term(getNestedVariableTypeField(), VariableType.STRING.getId()),
            filter);

    return nested(VARIABLES, containsVariableString, ChildScoreMode.None);
  }

  @Override
  protected Query createEqualsOneOrMoreValuesQuery(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    final Query query =
        createEqualsOneOrMoreValuesFilterQuery(
            dto.getName(), dto.getType(), dto.getData().getValues());

    return NOT_IN.equals(dto.getData().getOperator()) ? not(query) : query;
  }

  @Override
  protected Query createBooleanQuery(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createEqualsOneOrMoreValuesFilterQuery(
        dto.getName(), dto.getType(), dto.getData().getValues());
  }

  private Query createEqualsOneOrMoreValuesFilterQuery(
      final String variableName, final VariableType variableType, final List<?> values) {
    final BoolQuery.Builder variableFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");
    final String nestedVariableNameFieldLabel = getNestedVariableNameField();
    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);
    final List<?> nonNullValues =
        values.stream().filter(Objects::nonNull).collect(Collectors.toList());

    if (!nonNullValues.isEmpty()) {
      variableFilterBuilder.should(
          new NestedQuery.Builder()
              .path(VARIABLES)
              .query(
                  and(
                      term(nestedVariableNameFieldLabel, variableName),
                      term(getNestedVariableTypeField(), variableType.getId()),
                      terms(nestedVariableValueFieldLabel, nonNullValues)))
              .scoreMode(ChildScoreMode.None)
              .build()
              .toQuery());
    }

    final boolean hasNullValues = nonNullValues.size() < values.size();
    if (hasNullValues) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQuery(variableName, variableType));
    }
    return variableFilterBuilder.build().toQuery();
  }

  @Override
  protected Query createNumericQuery(final OperatorMultipleValuesVariableFilterDataDto dto) {
    OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto(dto);

    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();

    boolean isInOrNotIn = false;
    final List<Query> queries =
        new ArrayList<>(
            List.of(
                term(getNestedVariableNameField(), dto.getName()),
                term(getNestedVariableNameField(), dto.getType().getId())));

    final Object value = OperatorMultipleValuesVariableFilterDataDtoUtil.retrieveValue(dto);
    switch (data.getOperator()) {
      case IN, NOT_IN -> isInOrNotIn = true;
      case LESS_THAN -> queries.add(lt(nestedVariableValueFieldLabel, value));
      case GREATER_THAN -> queries.add(gt(nestedVariableValueFieldLabel, value));
      case LESS_THAN_EQUALS -> queries.add(lte(nestedVariableValueFieldLabel, value));
      case GREATER_THAN_EQUALS -> queries.add(gte(nestedVariableValueFieldLabel, value));
      default ->
          LOG.warn(
              "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
              data.getOperator(),
              dto.getType());
    }

    final Query query =
        new NestedQuery.Builder()
            .path(VARIABLES)
            .query(and(queries.toArray(new Query[0])))
            .scoreMode(ChildScoreMode.None)
            .build()
            .toQuery();
    return isInOrNotIn ? createEqualsOneOrMoreValuesQuery(dto) : query;
  }

  @Override
  protected Query createDateQuery(final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final BoolQuery.Builder dateFilterBuilder = new BoolQuery.Builder().minimumShouldMatch("1");

    if (dto.getData().isIncludeUndefined()) {
      dateFilterBuilder.should(createFilterForUndefinedOrNullQuery(dto.getName(), dto.getType()));
    } else if (dto.getData().isExcludeUndefined()) {
      dateFilterBuilder.should(createExcludeUndefinedOrNullQuery(dto.getName(), dto.getType()));
    }

    final BoolQuery.Builder dateValueFilterQueryBuilder =
        new BoolQuery.Builder()
            .must(term(getNestedVariableNameField(), dto.getName()))
            .must(term(getNestedVariableTypeField(), dto.getType().getId()));
    final List<Query> filterQueries =
        DateFilterQueryUtilOS.filterQueries(
            List.of(dto.getData()), getVariableValueFieldForType(dto.getType()), timezone);
    if (!filterQueries.isEmpty()) {
      dateValueFilterQueryBuilder.filter(filterQueries);
      dateFilterBuilder.should(
          nested(VARIABLES, dateValueFilterQueryBuilder.build().toQuery(), ChildScoreMode.None));
    }

    return dateFilterBuilder.build().toQuery();
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private Query createFilterForUndefinedOrNullQuery(
      final String variableName, final VariableType variableType) {
    return ProcessVariableHelperOS.createFilterForUndefinedOrNullQuery(variableName, variableType);
  }

  private Query createExcludeUndefinedOrNullQuery(
      final String variableName, final VariableType variableType) {
    return createExcludeUndefinedOrNullQueryFilter(variableName, variableType);
  }
}
