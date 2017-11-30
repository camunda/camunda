package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.UnfinishedProcessInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.service.es.schema.type.UnfinishedProcessInstanceTrackingType.PROCESS_INSTANCE_IDS;
import static org.camunda.optimize.service.es.schema.type.UnfinishedProcessInstanceTrackingType.UNFINISHED_PROCESS_INSTANCE_TRACKING_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsLookupQuery;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UnfinishedProcessInstanceImportIndexHandler extends ScrollBasedImportIndexHandler {

  private UnfinishedProcessInstanceCountFetcher unfinishedProcessInstanceCountFetcher;
  private String scrollId;

  public UnfinishedProcessInstanceImportIndexHandler(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @PostConstruct
  public void init() {
    unfinishedProcessInstanceCountFetcher = beanHelper.getInstance(UnfinishedProcessInstanceCountFetcher.class, this.engineAlias);
    super.init();
  }

  @Override
  public void resetScroll() {
    scrollId = null;
  }

  @Override
  public long fetchMaxEntityCount() {
    return unfinishedProcessInstanceCountFetcher.fetchUnfinishedHistoricProcessInstanceCount();
  }

  @Override
  public Set<String> fetchNextPageOfProcessInstanceIds() {
    logger.debug("Scrolling unfinished process instance ids");
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
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
        .setTypes(configurationService.getProcessInstanceType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setFetchSource(false)
        .setSize(configurationService.getEngineImportProcessInstanceMaxPageSize())
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
      .prepareRefresh()
      .get();
  }

  private QueryBuilder buildBasicQuery() {
    TermsLookup termsLookup = new TermsLookup(
      configurationService.getOptimizeIndex(UNFINISHED_PROCESS_INSTANCE_TRACKING_TYPE),
      UNFINISHED_PROCESS_INSTANCE_TRACKING_TYPE,
      EsHelper.constructKey(UNFINISHED_PROCESS_INSTANCE_TRACKING_TYPE, engineAlias),
      PROCESS_INSTANCE_IDS);
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    query
      .mustNot(existsQuery(START_DATE))
      .mustNot(termsLookupQuery(PROCESS_INSTANCE_ID, termsLookup));
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
    return UNFINISHED_PROCESS_INSTANCE_TRACKING_TYPE;
  }

}
