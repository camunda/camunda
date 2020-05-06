/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.createExcludeUndefinedOrNullQueryFilterBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@RequiredArgsConstructor
@Slf4j
@Component
public class ProcessVariableQueryFilter implements QueryFilter<VariableFilterDataDto<?>> {
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
    if (dto.isFilterForUndefined()) {
      return createFilterForUndefinedOrNullQueryBuilder(dto);
    }

    QueryBuilder queryBuilder = dto.isExcludeUndefined()
      ? createExcludeUndefinedOrNullQueryBuilder(dto)
      : matchAllQuery();

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
        queryBuilder = createDateQueryBuilder(dateVarDto);
        break;
      case BOOLEAN:
        BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder = createBoolQueryBuilder(booleanVarDto);
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

  private QueryBuilder createStringQueryBuilder(StringVariableFilterDataDto dto) {
    String operator = dto.getData().getOperator();
    if (operator.equals(IN)) {
      return createEqualityMultiValueQueryBuilder(dto);
    } else if (operator.equals(NOT_IN)) {
      return createInequalityMultiValueQueryBuilder(dto);
    } else {
      log.warn(
        "Could not filter for variables! Operator [{}] is not allowed for type [String]. Ignoring filter.",
        operator
      );
    }
    return boolQuery();
  }

  private BoolQueryBuilder createEqualityMultiValueQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String nestedVariableNameFieldLabel = getNestedVariableNameField();
    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    for (String value : dto.getData().getValues()) {
      boolQueryBuilder.should(
        nestedQuery(
          VARIABLES,
          boolQuery()
            .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
            .must(termQuery(nestedVariableValueFieldLabel, value))
            .must(termQuery(getNestedVariableTypeField(), dto.getType().getId())),
          ScoreMode.None
        )
      );
    }
    return boolQueryBuilder;
  }

  private BoolQueryBuilder createInequalityMultiValueQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String nestedVariableNameFieldLabel = getNestedVariableNameField();
    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    for (String value : dto.getData().getValues()) {
      boolQueryBuilder.mustNot(
        nestedQuery(
          VARIABLES,
          boolQuery()
            .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
            .must(termQuery(nestedVariableValueFieldLabel, value))
            .must(termQuery(getNestedVariableTypeField(), dto.getType().getId())),
          ScoreMode.None
        )
      );
    }
    return boolQueryBuilder;
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
        resultQuery = createEqualityMultiValueQueryBuilder(dto);
        break;
      case NOT_IN:
        resultQuery = createInequalityMultiValueQueryBuilder(dto);
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

  private void validateMultipleValuesFilterDataDto(final OperatorMultipleValuesVariableFilterDataDto dto) {
    if (CollectionUtils.isEmpty(dto.getData().getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private Object retrieveValue(OperatorMultipleValuesVariableFilterDataDto dto) {
    String value = dto.getData().getValues().get(0);
    switch (dto.getType()) {
      default:
      case STRING:
        return value;
      case INTEGER:
        return Integer.parseInt(value);
      case LONG:
        return Long.parseLong(value);
      case SHORT:
        return Short.parseShort(value);
      case DOUBLE:
        return Double.parseDouble(value);
      case DATE:
        return value;
    }
  }

  private QueryBuilder createDateQueryBuilder(DateVariableFilterDataDto dto) {
    ValidationHelper.ensureAtLeastOneNotNull(
      "date filter start/end value",
      dto.getData().getStart(),
      dto.getData().getEnd()
    );

    BoolQueryBuilder filterDateVariables = boolQuery()
      .must(termsQuery(getNestedVariableNameField(), dto.getName()))
      .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()));

    dateFilterQueryService.addFilters(
      filterDateVariables,
      Collections.singletonList(dto.getData()),
      getVariableValueFieldForType(dto.getType())
    );

    return nestedQuery(VARIABLES, filterDateVariables, ScoreMode.None);
  }

  private QueryBuilder createBoolQueryBuilder(BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureNotEmpty("boolean filter value", dto.getData().getValue());
    boolean value = dto.getData().getValue();
    BoolQueryBuilder filterBooleanVariables = boolQuery()
      .must(termsQuery(getNestedVariableNameField(), dto.getName()))
      .must(termQuery(getNestedVariableTypeField(), dto.getType().getId()))
      .must(termsQuery(getVariableValueFieldForType(dto.getType()), value));
    return nestedQuery(
      VARIABLES,
      filterBooleanVariables,
      ScoreMode.None
    );
  }

  private QueryBuilder createFilterForUndefinedOrNullQueryBuilder(final VariableFilterDataDto<?> dto) {
    return ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder(dto.getName(), dto.getType());
  }

  private QueryBuilder createExcludeUndefinedOrNullQueryBuilder(final VariableFilterDataDto<?> dto) {
    return createExcludeUndefinedOrNullQueryFilterBuilder(dto.getName(), dto.getType());
  }
}
