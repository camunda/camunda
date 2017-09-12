package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableNameFieldLabelForType;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableValueFieldLabelForType;
import static org.camunda.optimize.service.util.VariableHelper.variableTypeToFieldLabel;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class VariableFilter implements QueryFilter {

  private Logger logger = LoggerFactory.getLogger(VariableFilter.class);

  public void addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    this.addVariableFilters(query, filter.getVariables());
  }

  private void addVariableFilters(BoolQueryBuilder query, List<VariableFilterDto> variables) {
    if (variables != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDto variable : variables) {
        filters.add(createFilterQueryBuilder(variable));
      }
    }
  }

  private QueryBuilder createFilterQueryBuilder(VariableFilterDto dto) {
    QueryBuilder queryBuilder = matchAllQuery();
    switch (dto.getType().toLowerCase()) {
      case "string":
        queryBuilder =  createStringQueryBuilder(dto);
        break;
      case "integer":
      case "double":
      case "short":
      case "long":
        queryBuilder = createNumericQueryBuilder(dto);
        break;
      case "date":
        queryBuilder = createDateQueryBuilder(dto);
        break;
      case "boolean":
        queryBuilder =  createBoolQueryBuilder(dto);
        break;
      default:
        logger.error("Could not filter for variables! Type [{}] is not supported for variable filters.", dto.getType());
    }
    return queryBuilder;
  }

  private QueryBuilder createStringQueryBuilder(VariableFilterDto dto) {
    if (dto.getOperator().equals(IN)) {
      return createEqualityMultiValueQueryBuilder(dto);
    } else if (dto.getOperator().equals(NOT_IN)) {
      return createInequalityMultiValueQueryBuilder(dto);
    } else {
      logger.error("Could not filter for variables! Operator [{}] is not allowed for type [String]", dto.getOperator());
    }
    return boolQuery();
  }

  private BoolQueryBuilder createEqualityMultiValueQueryBuilder(VariableFilterDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String variableFieldLabel = variableTypeToFieldLabel(dto.getType());
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    for (String value : dto.getValues()) {
      boolQueryBuilder.should(
        nestedQuery(
          variableFieldLabel,
          boolQuery()
            .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
            .must(termQuery(nestedVariableValueFieldLabel, value)),
          ScoreMode.None)
      );
    }
    return boolQueryBuilder;
  }

  private BoolQueryBuilder createInequalityMultiValueQueryBuilder(VariableFilterDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String variableFieldLabel = variableTypeToFieldLabel(dto.getType());
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    for (String value : dto.getValues()) {
        boolQueryBuilder.mustNot(
          nestedQuery(
            variableFieldLabel,
            boolQuery()
                .must(termQuery(nestedVariableNameFieldLabel, dto.getName()))
                .must(termQuery(nestedVariableValueFieldLabel, value)),
            ScoreMode.None
          )
        );
      }
    return boolQueryBuilder;
  }

  private QueryBuilder createNumericQueryBuilder(VariableFilterDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (dto.getValues().size() < 1) {
      logger.error("Could not filter for variables! " +
        "There were no value provided for operator [{}] and type [{}]", dto.getOperator(), dto.getType());
      return boolQueryBuilder;
    }
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    QueryBuilder resultQuery = nestedQuery(
      variableTypeToFieldLabel(dto.getType()),
      boolQueryBuilder,
      ScoreMode.None);
    boolQueryBuilder.must(
      termQuery(nestedVariableNameFieldLabel, dto.getName())
    );
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    Object value = retrieveValue(dto);
    switch (dto.getOperator()) {
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
        logger.error("Could not filter for variables! Operator [{}] is not supported for type [{}]", dto.getOperator(), dto.getType());
    }
    return resultQuery;
  }

  private Object retrieveValue(VariableFilterDto dto) {
    String value = dto.getValues().get(0);
    switch (dto.getType().toLowerCase()) {
      case "string":
        return value;
      case "integer":
        return Integer.parseInt(value);
      case "long":
        return Long.parseLong(value);
      case "short":
        return Short.parseShort(value);
      case "double":
        return Double.parseDouble(value);
      case "boolean":
        return Boolean.parseBoolean(value);
      case "date":
        return value;
    }
    return value;
  }

  private QueryBuilder createDateQueryBuilder(VariableFilterDto dto) {
    return createNumericQueryBuilder(dto);
  }

  private QueryBuilder createBoolQueryBuilder(VariableFilterDto dto) {
    QueryBuilder queryBuilder = matchAllQuery();
    if (dto.getOperator().equals(EQUALS)) {
      queryBuilder =
        nestedQuery(
          BOOLEAN_VARIABLES,
          boolQuery()
            .must(
              termsQuery(getNestedVariableNameFieldLabelForType(dto.getType()), dto.getName())
            )
            .must(
              termsQuery(getNestedVariableValueFieldLabelForType(dto.getType()), retrieveValue(dto))
            ),
          ScoreMode.None
        );
    } else {
      logger.error("Could not filter for variables! Operator [{}] is not supported for type [Boolean]", dto.getOperator());
    }
    return queryBuilder;
  }

}
