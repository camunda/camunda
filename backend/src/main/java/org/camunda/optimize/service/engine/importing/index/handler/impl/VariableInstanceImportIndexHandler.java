package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.VariableInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DOUBLE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.INTEGER_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.LONG_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.SHORT_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.VariableProcessInstanceTrackingType.PROCESS_INSTANCE_IDS;
import static org.camunda.optimize.service.es.schema.type.VariableProcessInstanceTrackingType.VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsLookupQuery;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableInstanceImportIndexHandler extends ScrollBasedImportIndexHandler {

  @Autowired
  private VariableInstanceCountFetcher variableInstanceCountFetcher;

  private String scrollId;

  public VariableInstanceImportIndexHandler(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @Override
  public void resetScroll() {
    scrollId = null;
  }

  @Override
  public long fetchMaxEntityCount() {
    return variableInstanceCountFetcher.fetchVariableInstanceCount();
  }

  @Override
  public Set<String> fetchNextPageOfProcessInstanceIds() {
    logger.debug("Fetching process instance ids ");
    if (scrollId == null) {
      return performInitialSearchQuery();
    } else {
      return performScrollQuery();
    }
  }

  private Set<String> performScrollQuery() {
    logger.debug("Performing scroll search query!");
    Set<String> result = new HashSet<>();
    SearchResponse scrollResp =
      esclient
        .prepareSearchScroll(scrollId)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();

    logger.debug("Scroll search query got [{}] results", scrollResp.getHits().getTotalHits());

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  private Set<String> performInitialSearchQuery() {
    logger.debug("Performing initial search query!");
    performRefresh();
    Set<String> result = new HashSet<>();
    QueryBuilder query;
    query = buildBasicQuery();
    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getProcessInstanceType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setFetchSource(false)
        .setSize(configurationService.getEngineImportVariableInstanceMaxPageSize())
        .addSort(SortBuilders.fieldSort("startDate").order(SortOrder.ASC))
        .get();

    logger.debug("Initial search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  private void performRefresh() {
    esclient
      .admin()
      .indices()
      .prepareRefresh(configurationService.getOptimizeIndex())
      .get();
  }

  private QueryBuilder buildBasicQuery() {
    TermsLookup termsLookup = new TermsLookup(
      configurationService.getOptimizeIndex(),
      VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE,
      VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE,
      PROCESS_INSTANCE_IDS);
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    query
      .must(existsQuery(START_DATE))
      .mustNot(termsLookupQuery(PROCESS_INSTANCE_ID, termsLookup))
      .mustNot(existsQuery(BOOLEAN_VARIABLES))
      .mustNot(existsQuery(DOUBLE_VARIABLES))
      .mustNot(existsQuery(STRING_VARIABLES))
      .mustNot(existsQuery(SHORT_VARIABLES))
      .mustNot(existsQuery(LONG_VARIABLES))
      .mustNot(existsQuery(DATE_VARIABLES))
      .mustNot(existsQuery(INTEGER_VARIABLES));
    if (configurationService.getProcessDefinitionIdsToImport() != null &&
      !configurationService.getProcessDefinitionIdsToImport().isEmpty()) {
      for (String processDefinitionId : configurationService.getProcessDefinitionIdsToImport()) {
        query
          .should(QueryBuilders.termQuery("processDefinitionId", processDefinitionId));
      }
    }
    return query;
  }
  @Override
  protected String getElasticsearchTrackingType() {
    return VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE;
  }
}
