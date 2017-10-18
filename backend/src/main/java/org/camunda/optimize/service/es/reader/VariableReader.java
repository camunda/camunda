package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.query.variable.GetVariablesResponseDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.VariableHelper.getAllVariableTypeFieldLabels;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableNameFieldLabel;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableValueFieldLabel;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class VariableReader {

  public static final int MAX_VAR_SIZE = 10000;
  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  public static final String NAMES_AGGREGATION = "names";
  public static final String VALUE_AGGREGATION = "values";

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  private VariableExtractor variableExtractor;

  @PostConstruct
  public void init() {
    variableExtractor = new VariableExtractor(configurationService);
  }

  public List<GetVariablesResponseDto> getVariables(String processDefinitionId) {
    logger.debug("Fetching variables for process definition: {}", processDefinitionId);
    QueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_ID, processDefinitionId));

    SearchResponse scrollResp = queryElasticsearch(query);
    List<GetVariablesResponseDto> result = new ArrayList<>();

    do {
      Aggregations aggregations = scrollResp.getAggregations();
      if (aggregations != null) {
        result.addAll(variableExtractor.extractVariables(aggregations));
      }

      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return result;
  }

  private SearchResponse queryElasticsearch(QueryBuilder query) {
    SearchRequestBuilder requestBuilder =
      esclient
          .prepareSearch(configurationService.getOptimizeIndex())
          .setTypes(configurationService.getProcessInstanceType())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .setQuery(query);
    addAggregation(requestBuilder);

    return requestBuilder.get();
  }

  private void addAggregation(SearchRequestBuilder requestBuilder) {
    for (String variableFieldLabel : getAllVariableTypeFieldLabels()) {
      requestBuilder
      .addAggregation(
        createVariableAggregationForType(variableFieldLabel)
      );
    }
  }

  private AggregationBuilder createVariableAggregationForType(String variableFieldLabel) {
    return nested(variableFieldLabel, variableFieldLabel)
      .subAggregation(
        terms(NAMES_AGGREGATION)
          .field(getNestedVariableNameFieldLabel(variableFieldLabel))
          .size(MAX_VAR_SIZE)
          .subAggregation(
            createVariableValueAggregation(variableFieldLabel)
          )
      );
  }

  private TermsAggregationBuilder createVariableValueAggregation(String variableFieldLabel) {
    TermsAggregationBuilder termsAggregation = terms(VALUE_AGGREGATION)
      .field(getNestedVariableValueFieldLabel(variableFieldLabel))
      .size(configurationService.getMaxVariableValueListSize() + 1); // in order to check if the limit was exceeded
    if(variableFieldLabel.equals(DATE_VARIABLES)) {
      termsAggregation.format(configurationService.getDateFormat());
    }
    return termsAggregation;
  }

  public int getVariableInstanceCount(String engineAlias) {
    Long result;

    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();
    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getVariableType())
        .setQuery(query)
        .setFetchSource(false)
        .get();

    result = scrollResp.getHits().getTotalHits();
    return result.intValue();
  }
}
