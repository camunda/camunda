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
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.util.ProcessVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.util.ProcessVariableHelper.createExcludeUndefinedOrNullQueryFilterBuilder;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getValueSearchField;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.filter.util.DateFilterQueryUtil;
import io.camunda.optimize.service.util.ProcessVariableHelper;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractProcessVariableQueryFilter extends AbstractVariableQueryFilter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AbstractProcessVariableQueryFilter.class);

  public AbstractProcessVariableQueryFilter() {}

  protected QueryBuilder createFilterQueryBuilder(
      final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    QueryBuilder queryBuilder = matchAllQuery();

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
  protected QueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(
      final StringVariableFilterDataDto dto) {
    final BoolQueryBuilder containOneOfTheGivenStrings =
        createContainsOneOfTheGivenStringsQueryBuilder(dto.getName(), dto.getData().getValues());

    if (NOT_CONTAINS.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(containOneOfTheGivenStrings);
    } else {
      return containOneOfTheGivenStrings;
    }
  }

  @Override
  protected BoolQueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(
      final String variableName, final List<String> values) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery().minimumShouldMatch(1);

    values.stream()
        .filter(Objects::nonNull)
        .forEach(
            stringVal ->
                variableFilterBuilder.should(
                    createContainsGivenStringQuery(variableName, stringVal)));

    final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
    if (hasNullValues) {
      variableFilterBuilder.should(
          createFilterForUndefinedOrNullQueryBuilder(variableName, VariableType.STRING));
    }

    return variableFilterBuilder;
  }

  @Override
  protected QueryBuilder createContainsGivenStringQuery(
      final String variableName, final String valueToContain) {

    final BoolQueryBuilder containsVariableString =
        boolQuery()
            .must(termQuery(getNestedVariableNameField(), variableName))
            .must(termQuery(getNestedVariableTypeField(), VariableType.STRING.getId()));

    final String lowerCaseValue = valueToContain.toLowerCase(Locale.ENGLISH);
    final QueryBuilder filter =
        (lowerCaseValue.length() > MAX_GRAM)
            /*
              using the slow wildcard query for uncommonly large filter strings (> 10 chars)
            */
            ? wildcardQuery(
                getValueSearchField(LOWERCASE_FIELD), buildWildcardQuery(lowerCaseValue))
            /*
              using Elasticsearch ngrams to filter for strings < 10 chars,
              because it's fast but increasing the number of chars makes the index bigger
            */
            : termQuery(getValueSearchField(N_GRAM_FIELD), lowerCaseValue);

    containsVariableString.must(filter);

    return nestedQuery(VARIABLES, containsVariableString, ScoreMode.None);
  }

  @Override
  protected QueryBuilder createEqualsOneOrMoreValuesQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    final BoolQueryBuilder variableFilterBuilder =
        createEqualsOneOrMoreValuesFilterQuery(
            dto.getName(), dto.getType(), dto.getData().getValues());

    if (NOT_IN.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(variableFilterBuilder);
    } else {
      return variableFilterBuilder;
    }
  }

  @Override
  protected QueryBuilder createBooleanQueryBuilder(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createEqualsOneOrMoreValuesFilterQuery(
        dto.getName(), dto.getType(), dto.getData().getValues());
  }

  @Override
  protected QueryBuilder createNumericQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();
    final BoolQueryBuilder boolQueryBuilder =
        boolQuery()
            .must(termQuery(getNestedVariableNameField(), dto.getName()))
            .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));

    QueryBuilder resultQuery = nestedQuery(VARIABLES, boolQueryBuilder, ScoreMode.None);
    final Object value = retrieveValue(dto);
    switch (data.getOperator()) {
      case IN:
      case NOT_IN:
        resultQuery = createEqualsOneOrMoreValuesQueryBuilder(dto);
        break;
      case LESS_THAN:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).lt(value));
        break;
      case GREATER_THAN:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).gt(value));
        break;
      case LESS_THAN_EQUALS:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).lte(value));
        break;
      case GREATER_THAN_EQUALS:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).gte(value));
        break;
      default:
        log.warn(
            "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
            data.getOperator(),
            dto.getType());
    }
    return resultQuery;
  }

  @Override
  protected QueryBuilder createDateQueryBuilder(
      final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final BoolQueryBuilder dateFilterBuilder = boolQuery().minimumShouldMatch(1);

    if (dto.getData().isIncludeUndefined()) {
      dateFilterBuilder.should(
          createFilterForUndefinedOrNullQueryBuilder(dto.getName(), dto.getType()));
    } else if (dto.getData().isExcludeUndefined()) {
      dateFilterBuilder.should(
          createExcludeUndefinedOrNullQueryBuilder(dto.getName(), dto.getType()));
    }

    final BoolQueryBuilder dateValueFilterQuery =
        boolQuery()
            .must(termsQuery(getNestedVariableNameField(), dto.getName()))
            .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));
    DateFilterQueryUtil.addFilters(
        dateValueFilterQuery,
        Collections.singletonList(dto.getData()),
        getVariableValueFieldForType(dto.getType()),
        timezone);
    if (!dateValueFilterQuery.filter().isEmpty()) {
      dateFilterBuilder.should(nestedQuery(VARIABLES, dateValueFilterQuery, ScoreMode.None));
    }

    return dateFilterBuilder;
  }

  private BoolQueryBuilder createEqualsOneOrMoreValuesFilterQuery(
      final String variableName, final VariableType variableType, final List<?> values) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery().minimumShouldMatch(1);
    final String nestedVariableNameFieldLabel = getNestedVariableNameField();
    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);

    final List<?> nonNullValues =
        values.stream().filter(Objects::nonNull).collect(Collectors.toList());

    if (!nonNullValues.isEmpty()) {
      variableFilterBuilder.should(
          nestedQuery(
              VARIABLES,
              boolQuery()
                  .must(termQuery(nestedVariableNameFieldLabel, variableName))
                  .must(termQuery(getNestedVariableTypeField(), variableType.getId()))
                  .must(termsQuery(nestedVariableValueFieldLabel, nonNullValues)),
              ScoreMode.None));
    }

    final boolean hasNullValues = nonNullValues.size() < values.size();
    if (hasNullValues) {
      variableFilterBuilder.should(
          createFilterForUndefinedOrNullQueryBuilder(variableName, variableType));
    }
    return variableFilterBuilder;
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private QueryBuilder createFilterForUndefinedOrNullQueryBuilder(
      final String variableName, final VariableType variableType) {
    return ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder(
        variableName, variableType);
  }

  private QueryBuilder createExcludeUndefinedOrNullQueryBuilder(
      final String variableName, final VariableType variableType) {
    return createExcludeUndefinedOrNullQueryFilterBuilder(variableName, variableType);
  }
}
