package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.*;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.index.query.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.*;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.util.VariableHelper.*;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Component
public class VariableQueryFilter implements QueryFilter<VariableFilterDataDto> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private DateTimeFormatter formatter;

  @Override
  public void addFilters(BoolQueryBuilder query, List<VariableFilterDataDto> variables) {
    if (variables != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDataDto variable : variables) {
        filters.add(createFilterQueryBuilder(variable));
      }
    }
  }

  private QueryBuilder createFilterQueryBuilder(VariableFilterDataDto dto) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());
    QueryBuilder queryBuilder = matchAllQuery();
    switch (dto.getType().toLowerCase()) {
      case "string":
        StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        queryBuilder =  createStringQueryBuilder(stringVarDto);
        break;
      case "integer":
      case "double":
      case "short":
      case "long":
        OperatorMultipleValuesVariableFilterDataDto numericVarDto = (OperatorMultipleValuesVariableFilterDataDto) dto;
        queryBuilder = createNumericQueryBuilder(numericVarDto);
        break;
      case "date":
        DateVariableFilterDataDto dateVarDto = (DateVariableFilterDataDto) dto;
        queryBuilder = createDateQueryBuilder(dateVarDto);
        break;
      case "boolean":
        BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder =  createBoolQueryBuilder(booleanVarDto);
        break;
      default:
        logger.warn("Could not filter for variables! " +
                        "Type [{}] is not supported for variable filters. Ignoring filter.",
                dto.getType());
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
      logger.warn("Could not filter for variables! Operator [{}] is not allowed for type [String]. Ignoring filter.",
              operator);
    }
    return boolQuery();
  }

  private BoolQueryBuilder createEqualityMultiValueQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {

    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String variableFieldLabel = variableTypeToFieldLabel(dto.getType());
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    for (String value : dto.getData().getValues()) {
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

  private BoolQueryBuilder createInequalityMultiValueQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    String variableFieldLabel = variableTypeToFieldLabel(dto.getType());
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(dto.getType());
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(dto.getType());
    for (String value : dto.getData().getValues()) {
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

  private QueryBuilder createNumericQueryBuilder(OperatorMultipleValuesVariableFilterDataDto dto) {
    ValidationHelper.ensureNotNull("numeric filter values", dto.getData().getValues());
    OperatorMultipleValuesVariableFilterSubDataDto data = dto.getData();
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (data.getValues().size() < 1) {
      logger.warn("Could not filter for variables! " +
        "There were no value provided for operator [{}] and type [{}]. Ignoring filter.",
              data.getOperator(),
              dto.getType());
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
        logger.warn("Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
                data.getOperator(), dto.getType());
    }
    return resultQuery;
  }

  private Object retrieveValue(OperatorMultipleValuesVariableFilterDataDto dto) {
    String value = dto.getData().getValues().get(0);
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
      case "date":
        return value;
    }
    return value;
  }

  private QueryBuilder createDateQueryBuilder(DateVariableFilterDataDto dto) {
    String name = dto.getName();
    RangeQueryBuilder rangeQuery = createRangeQuery(dto);
    TermsQueryBuilder matchVariableName =
        termsQuery(getNestedVariableNameFieldLabelForType(dto.getType()), name);
    return nestedQuery(
        DATE_VARIABLES,
        boolQuery()
            .must(matchVariableName)
            .must(rangeQuery),
        ScoreMode.None
    );
  }

  private RangeQueryBuilder createRangeQuery(DateVariableFilterDataDto dto) {
    ValidationHelper.ensureAtLeastOneNotNull("date filter date value",
            dto.getData().getStart(), dto.getData().getEnd()
    );

    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(getNestedVariableValueFieldLabelForType(dto.getType()));
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

  private QueryBuilder createBoolQueryBuilder(BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureNotEmpty("boolean filter value", dto.getData().getValue());
    boolean value = Boolean.parseBoolean(dto.getData().getValue());
    TermsQueryBuilder matchVariableName =
        termsQuery(getNestedVariableNameFieldLabelForType(dto.getType()), dto.getName());
    TermsQueryBuilder matchBooleanValue =
        termsQuery(getNestedVariableValueFieldLabelForType(dto.getType()), value);
    return nestedQuery(
        BOOLEAN_VARIABLES,
        boolQuery()
            .must(matchVariableName)
            .must(matchBooleanValue),
        ScoreMode.None
    );
  }

}
