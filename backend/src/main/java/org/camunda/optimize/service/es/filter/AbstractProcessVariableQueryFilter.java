/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.filter.util.DateFilterQueryUtil;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.LOWERCASE_FIELD;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.N_GRAM_FIELD;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.buildWildcardQuery;
import static org.camunda.optimize.service.util.ProcessVariableHelper.createExcludeUndefinedOrNullQueryFilterBuilder;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getValueSearchField;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

@RequiredArgsConstructor
@Slf4j
@Component
public abstract class AbstractProcessVariableQueryFilter extends AbstractVariableQueryFilter {

  protected QueryBuilder createFilterQueryBuilder(final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    QueryBuilder queryBuilder = matchAllQuery();

    switch (dto.getType()) {
      case STRING:
        StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        queryBuilder = createStringQueryBuilder(stringVarDto);
        break;
      case INTEGER:
      case DOUBLE:
      case SHORT:
      case LONG:
        OperatorMultipleValuesVariableFilterDataDto numericVarDto = (OperatorMultipleValuesVariableFilterDataDto) dto;
        queryBuilder = createNumericQueryBuilder(numericVarDto);
        break;
      case DATE:
        DateVariableFilterDataDto dateVarDto = (DateVariableFilterDataDto) dto;
        queryBuilder = createDateQueryBuilder(dateVarDto, timezone);
        break;
      case BOOLEAN:
        BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder = createBooleanQueryBuilder(booleanVarDto);
        break;
      default:
        log.warn(
          "Could not filter for variables! " +
            "Type [{}] is not supported for variable filters. Ignoring filter.",
          dto.getType()
        );
    }
    return queryBuilder;
  }

  @Override
  protected QueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(final StringVariableFilterDataDto dto) {
    final BoolQueryBuilder containOneOfTheGivenStrings =
      createContainsOneOfTheGivenStringsQueryBuilder(dto.getName(), dto.getData().getValues());

    if (NOT_CONTAINS.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(containOneOfTheGivenStrings);
    } else {
      return containOneOfTheGivenStrings;
    }
  }

  @Override
  protected BoolQueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(final String variableName,
                                                                            final List<String> values) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery().minimumShouldMatch(1);

    values.stream()
      .filter(Objects::nonNull)
      .forEach(
        stringVal -> variableFilterBuilder.should(createContainsGivenStringQuery(variableName, stringVal))
      );

    final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
    if (hasNullValues) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(variableName, VariableType.STRING));
    }

    return variableFilterBuilder;
  }

  @Override
  protected QueryBuilder createContainsGivenStringQuery(final String variableName,
                                                        final String valueToContain) {

    final BoolQueryBuilder containsVariableString = boolQuery()
      .must(termQuery(getNestedVariableNameField(), variableName))
      .must(termQuery(getNestedVariableTypeField(), VariableType.STRING.getId()));

    final String lowerCaseValue = valueToContain.toLowerCase();
    QueryBuilder filter = (lowerCaseValue.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
      ? wildcardQuery(getValueSearchField(LOWERCASE_FIELD), buildWildcardQuery(lowerCaseValue))
          /*
            using Elasticsearch ngrams to filter for strings < 10 chars,
            because it's fast but increasing the number of chars makes the index bigger
          */
      : termQuery(getValueSearchField(N_GRAM_FIELD), lowerCaseValue);

    containsVariableString.must(filter);

    return nestedQuery(
      VARIABLES,
      containsVariableString,
      ScoreMode.None
    );
  }

  @Override
  protected QueryBuilder createEqualsOneOrMoreValuesQueryBuilder(final OperatorMultipleValuesVariableFilterDataDto dto) {
    final BoolQueryBuilder variableFilterBuilder = createEqualsOneOrMoreValuesFilterQuery(
      dto.getName(), dto.getType(), dto.getData().getValues()
    );

    if (NOT_IN.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(variableFilterBuilder);
    } else {
      return variableFilterBuilder;
    }
  }

  @Override
  protected QueryBuilder createBooleanQueryBuilder(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createEqualsOneOrMoreValuesFilterQuery(dto.getName(), dto.getType(), dto.getData().getValues());
  }

  private BoolQueryBuilder createEqualsOneOrMoreValuesFilterQuery(final String variableName,
                                                                  final VariableType variableType,
                                                                  final List<?> values) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery().minimumShouldMatch(1);
    final String nestedVariableNameFieldLabel = getNestedVariableNameField();
    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);

    final List<?> nonNullValues = values.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (!nonNullValues.isEmpty()) {
      variableFilterBuilder.should(
        nestedQuery(
          VARIABLES,
          boolQuery()
            .must(termQuery(nestedVariableNameFieldLabel, variableName))
            .must(termQuery(getNestedVariableTypeField(), variableType.getId()))
            .must(termsQuery(nestedVariableValueFieldLabel, nonNullValues)),
          ScoreMode.None
        )
      );
    }

    final boolean hasNullValues = nonNullValues.size() < values.size();
    if (hasNullValues) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(variableName, variableType));
    }
    return variableFilterBuilder;
  }

  @Override
  protected QueryBuilder createNumericQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();
    final BoolQueryBuilder boolQueryBuilder = boolQuery()
      .must(termQuery(getNestedVariableNameField(), dto.getName()))
      .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));

    QueryBuilder resultQuery = nestedQuery(VARIABLES, boolQueryBuilder, ScoreMode.None);
    Object value = retrieveValue(dto);
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
          data.getOperator(), dto.getType()
        );
    }
    return resultQuery;
  }

  @Override
  protected QueryBuilder createDateQueryBuilder(final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final BoolQueryBuilder dateFilterBuilder = boolQuery().minimumShouldMatch(1);

    if (dto.getData().isIncludeUndefined()) {
      dateFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(dto.getName(), dto.getType()));
    } else if (dto.getData().isExcludeUndefined()) {
      dateFilterBuilder.should(createExcludeUndefinedOrNullQueryBuilder(dto.getName(), dto.getType()));
    }

    final BoolQueryBuilder dateValueFilterQuery = boolQuery()
      .must(termsQuery(getNestedVariableNameField(), dto.getName()))
      .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));
    DateFilterQueryUtil.addFilters(
      dateValueFilterQuery,
      Collections.singletonList(dto.getData()),
      getVariableValueFieldForType(dto.getType()),
      timezone
    );
    if (!dateValueFilterQuery.filter().isEmpty()) {
      dateFilterBuilder.should(nestedQuery(VARIABLES, dateValueFilterQuery, ScoreMode.None));
    }

    return dateFilterBuilder;
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private QueryBuilder createFilterForUndefinedOrNullQueryBuilder(final String variableName,
                                                                  final VariableType variableType) {
    return ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder(variableName, variableType);
  }

  private QueryBuilder createExcludeUndefinedOrNullQueryBuilder(final String variableName,
                                                                final VariableType variableType) {
    return createExcludeUndefinedOrNullQueryFilterBuilder(variableName, variableType);
  }

}
