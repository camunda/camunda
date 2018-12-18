package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getAllVariableTypeFieldLabels;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabel;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldLabel;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class VariableReader {

  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  private static final String FILTER_FOR_NAME_AGGREGATION = "filterForName";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String NAMES_AGGREGATION = "names";
  private static final String VALUE_AGGREGATION = "values";
  private static final String STRING_VARIABLE_VALUE_NGRAM = "nGramField";
  private static final String STRING_VARIABLE_VALUE_LOWERCASE = "lowercaseField";


  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  public List<VariableRetrievalDto> getVariables(String processDefinitionKey,
                                                 String processDefinitionVersion,
                                                 String namePrefix) {
    logger.debug("Fetching variables for process definition with key [{}] and version [{}]",
      processDefinitionKey,
      processDefinitionVersion);

    BoolQueryBuilder query = buildProcessDefinitionBaseQuery(processDefinitionKey, processDefinitionVersion);

    SearchRequestBuilder requestBuilder =
      esclient
          .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_INSTANCE_TYPE))
          .setTypes(ElasticsearchConstants.PROC_INSTANCE_TYPE)
          .setQuery(query);
    addVariableAggregation(requestBuilder, namePrefix);
    SearchResponse response = requestBuilder.get();


    Aggregations aggregations = response.getAggregations();
    return extractVariables(aggregations);
  }

  public BoolQueryBuilder buildProcessDefinitionBaseQuery(String processDefinitionKey,
                                                          String processDefinitionVersion) {
    BoolQueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_KEY, processDefinitionKey));

    if (!ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      query = query
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion));
    }
    return query;
  }

  private List<VariableRetrievalDto> extractVariables(Aggregations aggregations) {
    List<VariableRetrievalDto> getVariablesResponseList = new ArrayList<>();
    for (String variableFieldLabel : ProcessVariableHelper.getAllVariableTypeFieldLabels()) {
      getVariablesResponseList.addAll(extractVariablesFromType(aggregations, variableFieldLabel));
    }
    return getVariablesResponseList;
  }

  private List<VariableRetrievalDto> extractVariablesFromType(Aggregations aggregations, String variableFieldLabel) {
    Nested variables = aggregations.get(variableFieldLabel);
    Filter filteredVariables = variables.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms nameTerms = filteredVariables.getAggregations().get(NAMES_AGGREGATION);
    List<VariableRetrievalDto> responseDtoList = new ArrayList<>();
    for (Terms.Bucket nameBucket : nameTerms.getBuckets()) {
      VariableRetrievalDto response = new VariableRetrievalDto();
      response.setName(nameBucket.getKeyAsString());
      response.setType(ProcessVariableHelper.fieldLabelToVariableType(variableFieldLabel));
      responseDtoList.add(response);
    }
    return responseDtoList;
  }

  private void addVariableAggregation(SearchRequestBuilder requestBuilder, String namePrefix) {
    String securedNamePrefix = namePrefix == null ? "" : namePrefix;
    for (String variableFieldLabel : getAllVariableTypeFieldLabels()) {
      FilterAggregationBuilder filterAllVariablesWithCertainPrefixInName = filter(
        FILTERED_VARIABLES_AGGREGATION,
        prefixQuery(getNestedVariableNameFieldLabel(variableFieldLabel), securedNamePrefix)
      );
      TermsAggregationBuilder collectAllVariableNames = terms(NAMES_AGGREGATION)
        .field(getNestedVariableNameFieldLabel(variableFieldLabel))
        .size(10_000)
        .order(BucketOrder.key(true));
      NestedAggregationBuilder checkoutVariables = nested(variableFieldLabel, variableFieldLabel);

      requestBuilder
        .addAggregation(
          checkoutVariables
            .subAggregation(
              filterAllVariablesWithCertainPrefixInName
                .subAggregation(
                  collectAllVariableNames
                )
            )
        );
    }
  }

  public List<String> getVariableValues(String processDefinitionKey,
                                        String processDefinitionVersion,
                                        String name,
                                        String type,
                                        String valueFilter) {
    logger.debug("Fetching variable values for process definition with key [{}] and version [{}]",
      processDefinitionKey,
      processDefinitionVersion);

    String variableFieldLabel = ProcessVariableHelper.variableTypeToFieldLabel(type);

    BoolQueryBuilder query = buildProcessDefinitionBaseQuery(processDefinitionKey, processDefinitionVersion);

    SearchResponse response =
      esclient
          .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_INSTANCE_TYPE))
          .setTypes(ElasticsearchConstants.PROC_INSTANCE_TYPE)
          .setQuery(query)
          .addAggregation(getVariableValueAggregation(name, variableFieldLabel, valueFilter))
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

  private AggregationBuilder getVariableValueAggregation(String name, String variableFieldLabel, String valueFilter) {
    TermsAggregationBuilder collectAllVariableValues =
      terms(VALUE_AGGREGATION)
        .field(getNestedVariableValueFieldLabel(variableFieldLabel))
        .size(10_000)
        .order(BucketOrder.key(true));

    if (DATE_VARIABLES.equals(variableFieldLabel)) {
      collectAllVariableValues.format(configurationService.getOptimizeDateFormat());
    }
    FilterAggregationBuilder filterForVariableWithGivenNameAndPrefix =
      getVariableValueFilterAggregation(name, variableFieldLabel, valueFilter);
    NestedAggregationBuilder checkoutVariables =
      nested(variableFieldLabel, variableFieldLabel);

    return
      checkoutVariables
        .subAggregation(
          filterForVariableWithGivenNameAndPrefix
            .subAggregation(
              collectAllVariableValues
            )
        );
  }

  private FilterAggregationBuilder getVariableValueFilterAggregation(String name,
                                                                     String variableFieldLabel,
                                                                     String valueFilter) {
    BoolQueryBuilder filterQuery = boolQuery()
        .must(termQuery(getNestedVariableNameFieldLabel(variableFieldLabel), name));
    addValueFilter(variableFieldLabel, valueFilter, filterQuery);
    return filter(
      FILTER_FOR_NAME_AGGREGATION,
      filterQuery
    );
  }

  private void addValueFilter(String variableFieldLabel, String valueFilter, BoolQueryBuilder filterQuery) {
    if (!(valueFilter == null) && !valueFilter.isEmpty() && STRING_VARIABLES.equals(variableFieldLabel)) {
      valueFilter = valueFilter.toLowerCase();
      QueryBuilder filter = (valueFilter.length() > IndexSettingsBuilder.MAX_GRAM)
          /*
            using the slow wildcard query for uncommonly large filter strings (> 10 chars)
          */
          ? wildcardQuery(
              getMultiFieldName(variableFieldLabel, STRING_VARIABLE_VALUE_LOWERCASE),
              buildWildcardQuery(valueFilter)
          )
          /*
            using Elasticsearch nGrams to filter for strings < 10 chars,
            because it's fast but increasing the number of chars makes the index bigger
          */
          : termQuery(
                  getMultiFieldName(variableFieldLabel, STRING_VARIABLE_VALUE_NGRAM),
                  valueFilter
          );

      filterQuery.must(filter);
    }
  }

  private String getMultiFieldName(String variableFieldLabel, String fieldName) {
    return getNestedVariableValueFieldLabel(variableFieldLabel) + "." + fieldName;
  }

  private String buildWildcardQuery(String valueFilter) {
    return "*" + valueFilter + "*";
  }
}
