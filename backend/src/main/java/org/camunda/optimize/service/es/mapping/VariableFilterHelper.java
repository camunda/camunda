package org.camunda.optimize.service.es.mapping;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.dto.optimize.VariableFilterDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_BOOLEAN_VALUE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_DATE_VALUE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_DOUBLE_VALUE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_INTEGER_VALUE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_LONG_VALUE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_SHORT_VALUE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_STRING_VALUE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class VariableFilterHelper {

  private Logger logger = LoggerFactory.getLogger(VariableFilterHelper.class);

  private Map<String,String> typeToVariableValueName;
  
  @PostConstruct
  private void init() {
    typeToVariableValueName = new HashMap<>();
    typeToVariableValueName.put("string", VARIABLE_STRING_VALUE);
    typeToVariableValueName.put("integer", VARIABLE_INTEGER_VALUE);
    typeToVariableValueName.put("short", VARIABLE_SHORT_VALUE);
    typeToVariableValueName.put("long", VARIABLE_LONG_VALUE);
    typeToVariableValueName.put("double", VARIABLE_DOUBLE_VALUE);
    typeToVariableValueName.put("boolean", VARIABLE_BOOLEAN_VALUE);
    typeToVariableValueName.put("date", VARIABLE_DATE_VALUE);
  }

  public BoolQueryBuilder addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    return this.addVariableFilters(query, filter.getVariables());
  }

  private BoolQueryBuilder addVariableFilters(BoolQueryBuilder query, List<VariableFilterDto> variables) {
    if (variables != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDto variable : variables) {
        BoolQueryBuilder boolQueryBuilder =
          boolQuery()
            .must(matchVariableName(variable.getName()))
            .must(matchVariableType(variable.getType()))
            .must(createFilterQueryBuilder(variable));
        filters.add(boolQueryBuilder);
      }
    }
    return query;
  }

  private NestedQueryBuilder matchVariableType(String variableType) {
    return nestedQuery(
      ProcessInstanceType.VARIABLES,
      termsQuery("variables.type", variableType),
      ScoreMode.None
    );
  }

  private NestedQueryBuilder matchVariableName(String variableName) {
    return nestedQuery(
      ProcessInstanceType.VARIABLES,
      termsQuery("variables.name", variableName),
      ScoreMode.None
    );
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
    NestedQueryBuilder variableValueQuery = nestedQuery("variables.value", queryBuilder, ScoreMode.None);
    return nestedQuery(VARIABLES, variableValueQuery, ScoreMode.None);
  }

  private QueryBuilder createStringQueryBuilder(VariableFilterDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (dto.getOperator().equals("=")) {
      for (String value : dto.getValues()) {
        boolQueryBuilder
          .must(termQuery(getVariableValueFieldName(dto.getType()), value));
      }
    } else if (dto.getOperator().equals("!=")) {
      for (String value : dto.getValues()) {
        boolQueryBuilder
          .mustNot(termQuery(getVariableValueFieldName(dto.getType()), value));
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
    Object value = retrieveValue(dto);
    String variableValueFieldName = getVariableValueFieldName(dto.getType());
    switch (dto.getOperator()) {
      case "=":
        boolQueryBuilder.must(termQuery(variableValueFieldName, value));
        break;
      case "!=":
        boolQueryBuilder.mustNot(termQuery(variableValueFieldName, value));
        break;
      case "<":
        boolQueryBuilder.must(rangeQuery(variableValueFieldName).lt(value));
        break;
      case ">":
        boolQueryBuilder.must(rangeQuery(variableValueFieldName).gt(value));
        break;
      case "<=":
        boolQueryBuilder.must(rangeQuery(variableValueFieldName).lte(value));
        break;
      case ">=":
        boolQueryBuilder.must(rangeQuery(variableValueFieldName).gte(value));
        break;
      default:
        logger.error("Could not filter for variables! Operator [{}] is not supported for type [{}]", dto.getOperator(), dto.getType());
    }
    return boolQueryBuilder;
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

  private String getVariableValueFieldName(String variableType) {
    return "variables.value." + typeToVariableValueName.get(variableType.toLowerCase());
  }

  private QueryBuilder createDateQueryBuilder(VariableFilterDto dto) {
    return createNumberQueryBuilder(dto);
  }

  private QueryBuilder createBoolQueryBuilder(VariableFilterDto dto) {
    BoolQueryBuilder boolQuery = boolQuery();
    if (dto.getOperator().equals("=")) {
      boolQuery
        .must(termQuery(getVariableValueFieldName(dto.getType()), retrieveValue(dto)));
    } else {
      logger.error("Could not filter for variables! Operator [{}] is not supported for type [Boolean]", dto.getOperator());
    }
    return boolQuery;
  }

}
