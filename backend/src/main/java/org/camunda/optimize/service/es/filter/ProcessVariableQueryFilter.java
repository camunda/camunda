/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.createExcludeUndefinedOrNullQueryFilterBuilder;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@RequiredArgsConstructor
@Slf4j
@Component
public class ProcessVariableQueryFilter extends AbstractVariableQueryFilter
  implements QueryFilter<VariableFilterDataDto<?>> {
  private final DateFilterQueryService dateFilterQueryService;

  @Override
  public void addFilters(BoolQueryBuilder query, List<VariableFilterDataDto<?>> variables) {
    if (variables != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDataDto<?> variable : variables) {
        filters.add(createFilterQueryBuilder(variable));
      }
    }
  }

  private QueryBuilder createFilterQueryBuilder(VariableFilterDataDto<?> dto) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    QueryBuilder queryBuilder = matchAllQuery();

    switch (dto.getType()) {
      case STRING:
        StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        queryBuilder = createMultiValueQueryBuilder(stringVarDto);
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
        queryBuilder = createDateQueryBuilder(dateVarDto);
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

  private QueryBuilder createMultiValueQueryBuilder(final OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    final BoolQueryBuilder variableFilterBuilder = createMultiValueVariableFilterQuery(
      dto.getName(), dto.getType(), dto.getData().getValues()
    );

    if (FilterOperatorConstants.NOT_IN.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(variableFilterBuilder);
    } else {
      return variableFilterBuilder;
    }
  }

  private QueryBuilder createBooleanQueryBuilder(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createMultiValueVariableFilterQuery(dto.getName(), dto.getType(), dto.getData().getValues());
  }

  private BoolQueryBuilder createMultiValueVariableFilterQuery(final String variableName,
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

    if (nonNullValues.size() < values.size()) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(variableName, variableType));
    }
    return variableFilterBuilder;
  }

  private QueryBuilder createNumericQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesVariableFilterSubDataDto data = dto.getData();
    final BoolQueryBuilder boolQueryBuilder = boolQuery()
      .must(termQuery(getNestedVariableNameField(), dto.getName()))
      .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));

    QueryBuilder resultQuery = nestedQuery(VARIABLES, boolQueryBuilder, ScoreMode.None);
    Object value = retrieveValue(dto);
    switch (data.getOperator()) {
      case IN:
      case NOT_IN:
        resultQuery = createMultiValueQueryBuilder(dto);
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

  private QueryBuilder createDateQueryBuilder(final DateVariableFilterDataDto dto) {
    final BoolQueryBuilder dateFilterBuilder = boolQuery().minimumShouldMatch(1);

    if (dto.getData().isIncludeUndefined()) {
      dateFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(dto.getName(), dto.getType()));
    } else if (dto.getData().isExcludeUndefined()) {
      dateFilterBuilder.should(createExcludeUndefinedOrNullQueryBuilder(dto.getName(), dto.getType()));
    }

    final BoolQueryBuilder dateValueFilterQuery = boolQuery()
      .must(termsQuery(getNestedVariableNameField(), dto.getName()))
      .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));
    dateFilterQueryService.addFilters(
      dateValueFilterQuery, Collections.singletonList(dto.getData()), getVariableValueFieldForType(dto.getType())
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
