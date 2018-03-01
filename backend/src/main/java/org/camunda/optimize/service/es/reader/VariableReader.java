package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.util.VariableHelper.getAllVariableTypeFieldLabels;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableNameFieldLabel;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableValueFieldLabel;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class VariableReader {

  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  private static final String FILTER_FOR_NAME_AGGREGATION = "filterForName";
  private static final String NAMES_AGGREGATION = "names";
  private static final String VALUE_AGGREGATION = "values";

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  public List<VariableRetrievalDto> getVariables(String processDefinitionKey, String processDefinitionVersion) {
    logger.debug("Fetching variables for process definition with key [{}] and version [{}]",
      processDefinitionKey,
      processDefinitionVersion);
    QueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion));

    SearchRequestBuilder requestBuilder =
      esclient
          .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
          .setTypes(configurationService.getProcessInstanceType())
          .setQuery(query);
    addVariableAggregation(requestBuilder);
    SearchResponse response = requestBuilder.get();


    Aggregations aggregations = response.getAggregations();
    return extractVariables(aggregations);
  }

  private List<VariableRetrievalDto> extractVariables(Aggregations aggregations) {
    List<VariableRetrievalDto> getVariablesResponseList = new ArrayList<>();
    for (String variableFieldLabel : VariableHelper.getAllVariableTypeFieldLabels()) {
      getVariablesResponseList.addAll(extractVariablesFromType(aggregations, variableFieldLabel));
    }
    return getVariablesResponseList;
  }

  private List<VariableRetrievalDto> extractVariablesFromType(Aggregations aggregations, String variableFieldLabel) {
    Nested stringVariables = aggregations.get(variableFieldLabel);
    Terms nameTerms = stringVariables.getAggregations().get(NAMES_AGGREGATION);
    List<VariableRetrievalDto> responseDtoList = new ArrayList<>();
    for (Terms.Bucket nameBucket : nameTerms.getBuckets()) {
      VariableRetrievalDto response = new VariableRetrievalDto();
      response.setName(nameBucket.getKeyAsString());
      response.setType(VariableHelper.fieldLabelToVariableType(variableFieldLabel));
      responseDtoList.add(response);
    }
    return responseDtoList;
  }

  private void addVariableAggregation(SearchRequestBuilder requestBuilder) {
    for (String variableFieldLabel : getAllVariableTypeFieldLabels()) {
      requestBuilder
        .addAggregation(
          nested(variableFieldLabel, variableFieldLabel)
            .subAggregation(
              terms(NAMES_AGGREGATION)
                .field(getNestedVariableNameFieldLabel(variableFieldLabel))
                .order(BucketOrder.key(true))
            )
        );
    }
  }

  public List<String> getVariableValues(String processDefinitionKey, String processDefinitionVersion, String name, String type) {
    logger.debug("Fetching variable values for process definition with key [{}] and version [{}]",
      processDefinitionKey,
      processDefinitionVersion);
    QueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion));

    String variableFieldLabel = VariableHelper.variableTypeToFieldLabel(type);
    SearchResponse response =
      esclient
          .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
          .setTypes(configurationService.getProcessInstanceType())
          .setQuery(query)
          .addAggregation(getVariableValueAggregation(name, variableFieldLabel))
          .get();

    Aggregations aggregations = response.getAggregations();
    return extractVariableValues(aggregations, variableFieldLabel);
  }

  private List<String> extractVariableValues(Aggregations aggregations, String variableFieldLabel) {
    Nested variablesFromType = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variablesFromType.getAggregations().get(FILTER_FOR_NAME_AGGREGATION);
    Terms valueTerms = filteredVariables.getAggregations().get(VALUE_AGGREGATION);
    List<String> allValues = new ArrayList<>();
    for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
      allValues.add(valueBucket.getKeyAsString());
    }
    return allValues;
  }

  private AggregationBuilder getVariableValueAggregation(String name, String variableFieldLabel) {
    TermsAggregationBuilder variableValuesAgg =
      terms(VALUE_AGGREGATION)
        .field(getNestedVariableValueFieldLabel(variableFieldLabel))
        .order(BucketOrder.key(true));
    if (DATE_VARIABLES.equals(variableFieldLabel)) {
      variableValuesAgg.format(configurationService.getOptimizeDateFormat());
    }
    return
      nested(variableFieldLabel, variableFieldLabel)
        .subAggregation(
          filter(
            FILTER_FOR_NAME_AGGREGATION,
            termQuery(getNestedVariableNameFieldLabel(variableFieldLabel), name)
          )
            .subAggregation(
              variableValuesAgg
            )
        );
  }

}
