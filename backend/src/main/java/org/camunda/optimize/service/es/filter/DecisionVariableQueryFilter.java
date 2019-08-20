/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.DecisionVariableHelper;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class DecisionVariableQueryFilter implements QueryFilter<VariableFilterDataDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private final DateTimeFormatter formatter;

  DecisionVariableQueryFilter(final DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  abstract String getVariablePath();

  @Override
  public void addFilters(BoolQueryBuilder query, List<VariableFilterDataDto> variableFilters) {
    if (variableFilters != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDataDto variable : variableFilters) {
        filters.add(createFilterQueryBuilder(variable));
      }
    }
  }

  private QueryBuilder createFilterQueryBuilder(VariableFilterDataDto dto) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());
    if (dto.isFilterForUndefined()) {
      return createFilterUndefinedQueryBuilder(dto);
    }

    QueryBuilder queryBuilder = matchAllQuery();
    switch (dto.getType()) {
      case BOOLEAN:
        BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder = createBooleanQueryBuilder(booleanVarDto);
        break;
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
      default:
        logger.warn(
          "Could not filter for variables! Type [{}] is not supported for variable filters. Ignoring filter.",
          dto.getType()
        );
    }
    return queryBuilder;
  }

  private BoolQueryBuilder createBooleanQueryBuilder(BooleanVariableFilterDataDto dto) {

    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String nestedVariableNameFieldLabel = getVariableIdField();
    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    boolQueryBuilder.must(
      nestedQuery(
        getVariablePath(),
        boolQuery()
          .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
          .must(termQuery(nestedVariableValueFieldLabel, dto.getData().getValue())),
        ScoreMode.None
      )
    );
    return boolQueryBuilder;
  }

  private QueryBuilder createStringQueryBuilder(StringVariableFilterDataDto dto) {
    String operator = dto.getData().getOperator();
    if (operator.equals(IN)) {
      return createEqualityMultiValueQueryBuilder(dto);
    } else if (operator.equals(NOT_IN)) {
      return createInequalityMultiValueQueryBuilder(dto);
    } else {
      logger.warn(
        "Could not filter for variables! Operator [{}] is not allowed for type [String]. Ignoring filter.",
        operator
      );
    }
    return boolQuery();
  }

  private BoolQueryBuilder createEqualityMultiValueQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {

    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String nestedVariableNameFieldLabel = getVariableIdField();
    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    for (String value : dto.getData().getValues()) {
      boolQueryBuilder.should(
        nestedQuery(
          getVariablePath(),
          boolQuery()
            .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
            .must(termQuery(nestedVariableValueFieldLabel, value)),
          ScoreMode.None
        )
      );
    }
    return boolQueryBuilder;
  }

  private BoolQueryBuilder createInequalityMultiValueQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String nestedVariableNameFieldLabel = getVariableIdField();
    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    for (String value : dto.getData().getValues()) {
      boolQueryBuilder.mustNot(
        nestedQuery(
          getVariablePath(),
          boolQuery()
            .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
            .must(termQuery(nestedVariableValueFieldLabel, value)),
          ScoreMode.None
        )
      );
    }
    return boolQueryBuilder;
  }

  private QueryBuilder createNumericQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    ValidationHelper.ensureNotNull("numeric filter values", dto.getData().getValues());

    String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesVariableFilterSubDataDto data = dto.getData();
    final BoolQueryBuilder boolQueryBuilder = boolQuery().must(
      termQuery(getVariableIdField(), dto.getName())
    );

    if (data.getValues().size() < 1) {
      logger.warn(
        "Could not filter for variables! No values provided for operator [{}] and type [{}]. Ignoring filter.",
        data.getOperator(),
        dto.getType()
      );
      return boolQueryBuilder;
    }

    QueryBuilder resultQuery = nestedQuery(getVariablePath(), boolQueryBuilder, ScoreMode.None);
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
        logger.warn(
          "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
          data.getOperator(), dto.getType()
        );
    }
    return resultQuery;
  }

  private QueryBuilder createDateQueryBuilder(DateVariableFilterDataDto dto) {
    final RangeQueryBuilder rangeQuery = createRangeQuery(dto);
    return nestedQuery(
      getVariablePath(),
      boolQuery()
        .must(termQuery(getVariableIdField(), dto.getName()))
        .must(rangeQuery),
      ScoreMode.None
    );
  }

  private QueryBuilder createFilterUndefinedQueryBuilder(VariableFilterDataDto dto) {
    return boolQuery().mustNot(nestedQuery(
      getVariablePath(),
      termQuery(getVariableIdField(), dto.getName()),
      ScoreMode.None
    ));
  }

  private RangeQueryBuilder createRangeQuery(DateVariableFilterDataDto dto) {
    ValidationHelper.ensureAtLeastOneNotNull(
      "date filter date value", dto.getData().getStart(), dto.getData().getEnd()
    );

    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(getVariableValueFieldForType(dto.getType()));
    if (dto.getData().getEnd() != null) {
      String endAsString = formatter.format(dto.getData().getEnd());
      queryDate.lte(endAsString);
    }
    if (dto.getData().getStart() != null) {
      String startAsString = formatter.format(dto.getData().getStart());
      queryDate.gte(startAsString);
    }
    return queryDate;
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return DecisionVariableHelper.getVariableValueFieldForType(getVariablePath(), type);
  }

  private String getVariableIdField() {
    return DecisionVariableHelper.getVariableClauseIdField(getVariablePath());
  }

  private Object retrieveValue(OperatorMultipleValuesVariableFilterDataDto dto) {
    String value = dto.getData().getValues().get(0);
    switch (dto.getType()) {
      default:
      case STRING:
      case DATE:
        return value;
      case INTEGER:
        return Integer.parseInt(value);
      case LONG:
        return Long.parseLong(value);
      case SHORT:
        return Short.parseShort(value);
      case DOUBLE:
        return Double.parseDouble(value);
    }
  }

}
