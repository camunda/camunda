package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlTrackingType.PROCESS_DEFINITION_IDS;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlTrackingType.PROCESS_DEFINITION_XML_TRACKING_TYPE;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_ID;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsLookupQuery;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlImportIndexHandler extends ScrollBasedImportIndexHandler {

  public ProcessDefinitionXmlImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  protected Set<String> performScrollQuery() {
    logger.debug("Performing scroll search query!");
    Set<String> result = new HashSet<>();
    SearchResponse scrollResp =
      esclient
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
      .prepareRefresh(configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()))
      .get();
  }

  private QueryBuilder buildBasicQuery() {
    TermsLookup termsLookup = new TermsLookup(
      configurationService.getOptimizeIndex(PROCESS_DEFINITION_XML_TRACKING_TYPE),
      PROCESS_DEFINITION_XML_TRACKING_TYPE,
      EsHelper.constructKey(PROCESS_DEFINITION_XML_TRACKING_TYPE, engineContext.getEngineAlias()),
      PROCESS_DEFINITION_IDS);
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    query
      .must(termQuery(ENGINE, engineContext.getEngineAlias()))
      .mustNot(termsLookupQuery(PROCESS_DEFINITION_ID, termsLookup));
    if (configurationService.areProcessDefinitionsToImportDefined()) {
      for (String processDefinitionId : configurationService.getProcessDefinitionIdsToImport()) {
        query
          .should(termQuery(PROCESS_DEFINITION_ID, processDefinitionId));
      }
    }
    return query;
  }

  @Override
  protected Set<String> performInitialSearchQuery() {
    logger.debug("Performing initial search query!");
    performRefresh();
    Set<String> result = new HashSet<>();
    QueryBuilder query;
    query = buildBasicQuery();
    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()))
        .setTypes(configurationService.getProcessDefinitionType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setFetchSource(false)
        .setSize(configurationService.getEngineImportProcessDefinitionXmlMaxPageSize())
        .addSort(SortBuilders.fieldSort(PROCESS_DEFINITION_ID).order(SortOrder.DESC))
        .get();

    logger.debug("Initial search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  @Override
  protected long fetchMaxEntityCount() {
    // here the import index is based on process definition ids and therefore
    // we need to fetch the maximum number of process definitions
    performRefresh();

    SearchResponse response;
    if (scrollId != null) {
      response =
      esclient
        .prepareSearchScroll(scrollId)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } else {
      response = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()))
        .setTypes(configurationService.getProcessDefinitionType())
        .setQuery(buildBasicQuery())
        .setSize(0) // Don't return any documents, we don't need them.
        .get();
    }
    return response.getHits().getTotalHits();
  }

  @Override
  protected String getElasticsearchTrackingType() {
    return PROCESS_DEFINITION_XML_TRACKING_TYPE;
  }
}
