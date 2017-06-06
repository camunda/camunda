package org.camunda.optimize.service.es.mapping;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.variable.VariableFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
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
public class VariableFilterHelper {

  private Logger logger = LoggerFactory.getLogger(VariableFilterHelper.class);

  public BoolQueryBuilder addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    return this.addVariableFilters(query, filter.getVariables());
  }

  private BoolQueryBuilder addVariableFilters(BoolQueryBuilder query, List<VariableFilterDto> variables) {
    if (variables != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDto variable : variables) {
        filters.add(createFilterQueryBuilder(variable));
      }
    }
    return query;
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
        queryBuilder = createNumberQueryBuilder(dto);
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
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String variableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    String variableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    if (dto.getOperator().equals("=")) {
      for (String value : dto.getValues()) {
       boolQueryBuilder.should(
         nestedQuery(
           STRING_VARIABLES,
            boolQuery()
              .must(termQuery(variableNameFieldLabel, dto.getName()))
              .must(termQuery(variableValueFieldLabel, value)),
            ScoreMode.None)
       );
      }
    } else if (dto.getOperator().equals("!=")) {
      for (String value : dto.getValues()) {
        boolQueryBuilder.mustNot(
          nestedQuery(
            STRING_VARIABLES,
            boolQuery()
                .must(termQuery(variableNameFieldLabel, dto.getName()))
                .must(termQuery(variableValueFieldLabel, value)),
            ScoreMode.None
          )
        );
      }
    } else {
      logger.error("Could not filter for variables! Operator [{}] is not allowed for type [String]", dto.getOperator());
    }
    return boolQueryBuilder;
  }

  private QueryBuilder createNumberQueryBuilder(VariableFilterDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (dto.getValues().size() < 1) {
      logger.error("Could not filter for variables! " +
        "There were no value provided for operator [{}] and type [{}]", dto.getOperator(), dto.getType());
      return boolQueryBuilder;
    }
    QueryBuilder resultQuery = nestedQuery(
      variableTypeToFieldLabel(dto.getType().toLowerCase()),
      boolQueryBuilder,
      ScoreMode.None);
    String variableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    boolQueryBuilder.must(
      termQuery(variableNameFieldLabel, dto.getName())
    );
    String variableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    Object value = retrieveValue(dto);
    switch (dto.getOperator()) {
      case "=":
        boolQueryBuilder.must(termQuery(variableValueFieldLabel, value));
        break;
      case "!=":
        boolQueryBuilder.must(termQuery(variableValueFieldLabel, value));
        resultQuery = boolQuery().mustNot(resultQuery);
        break;
      case "<":
        boolQueryBuilder.must(rangeQuery(variableValueFieldLabel).lt(value));
        break;
      case ">":
        boolQueryBuilder.must(rangeQuery(variableValueFieldLabel).gt(value));
        break;
      case "<=":
        boolQueryBuilder.must(rangeQuery(variableValueFieldLabel).lte(value));
        break;
      case ">=":
        boolQueryBuilder.must(rangeQuery(variableValueFieldLabel).gte(value));
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
    return createNumberQueryBuilder(dto);
  }

  private QueryBuilder createBoolQueryBuilder(VariableFilterDto dto) {
    QueryBuilder queryBuilder = matchAllQuery();
    if (dto.getOperator().equals("=")) {
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
