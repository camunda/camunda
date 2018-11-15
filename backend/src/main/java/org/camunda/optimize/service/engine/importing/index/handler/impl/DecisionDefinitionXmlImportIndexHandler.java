package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.ENGINE;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlImportIndexHandler extends ScrollBasedImportIndexHandler {

  private static final String DECISION_DEFINITION_XML_IMPORT_INDEX_DOC_ID = "decisionDefinitionXmlImportIndex";

  public DecisionDefinitionXmlImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  protected Set<String> performScrollQuery() {
    logger.debug("Performing scroll search query!");

    final Set<String> result = new HashSet<>();
    final SearchResponse scrollResp = esclient
      .prepareSearchScroll(scrollId)
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .get();
    logger.debug("Scroll search query got [{}] results", scrollResp.getHits().getHits().length);

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
      .prepareRefresh(getOptimizeIndexAliasForType(ElasticsearchConstants.DECISION_DEFINITION_TYPE))
      .get();
  }

  private QueryBuilder buildBasicQuery() {
    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .mustNot(existsQuery(DecisionDefinitionType.DECISION_DEFINITION_XML))
      .must(termQuery(ENGINE, engineContext.getEngineAlias()));
    return query;
  }

  @Override
  protected Set<String> performInitialSearchQuery() {
    performRefresh();

    logger.debug("Performing initial search query!");
    final Set<String> result = new HashSet<>();
    final QueryBuilder query = buildBasicQuery();
    final SearchResponse scrollResp = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.DECISION_DEFINITION_TYPE))
      .setTypes(ElasticsearchConstants.DECISION_DEFINITION_TYPE)
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setFetchSource(false)
      .setSize(configurationService.getEngineImportDecisionDefinitionXmlMaxPageSize())
      .addSort(SortBuilders.fieldSort(DecisionDefinitionType.DECISION_DEFINITION_ID).order(SortOrder.DESC))
      .get();
    logger.debug("Initial search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  @Override
  protected String getElasticsearchTypeForStoring() {
    return DECISION_DEFINITION_XML_IMPORT_INDEX_DOC_ID;
  }
}
