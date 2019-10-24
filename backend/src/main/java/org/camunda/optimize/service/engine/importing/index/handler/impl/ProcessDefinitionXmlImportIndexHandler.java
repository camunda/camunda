/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlImportIndexHandler extends ScrollBasedImportIndexHandler {

  private static final String PROCESS_DEFINITION_XML_IMPORT_INDEX_DOC_ID = "processDefinitionXmlImportIndex";

  public ProcessDefinitionXmlImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  protected Set<String> performScrollQuery() {
    logger.debug("Performing scroll search query!");
    Set<String> result = new HashSet<>();

    SearchResponse scrollResp;
    try {
      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueSeconds(configurationService.getElasticsearchScrollTimeout()));
      scrollResp = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Could not scroll through process definitions.";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    logger.debug("Scroll search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  private void performRefresh() {
    RefreshRequest refreshAllRequest = new RefreshRequest();

    try {
      esClient.getHighLevelClient().indices().refresh(refreshAllRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Could not refresh Optimize indexes!", e);
      throw new OptimizeRuntimeException("Could not refresh Optimize indexes!", e);
    }
  }

  private QueryBuilder buildBasicQuery() {
    return QueryBuilders.boolQuery()
      .mustNot(existsQuery(PROCESS_DEFINITION_XML))
      .must(termQuery(ENGINE, engineContext.getEngineAlias()));
  }

  @Override
  protected Set<String> performInitialSearchQuery() {
    logger.debug("Performing initial search query!");
    performRefresh();
    Set<String> result = new HashSet<>();
    QueryBuilder query = buildBasicQuery();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .sort(SortBuilders.fieldSort(PROCESS_DEFINITION_ID).order(SortOrder.DESC))
      .size(configurationService.getEngineImportProcessDefinitionXmlMaxPageSize());
    SearchRequest searchRequest = new SearchRequest(PROCESS_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Was not able to scroll for process definitions!", e);
      throw new OptimizeRuntimeException("Was not able to scroll for process definitions!", e);
    }

    logger.debug("Initial search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  @Override
  protected String getElasticsearchTypeForStoring() {
    return PROCESS_DEFINITION_XML_IMPORT_INDEX_DOC_ID;
  }
}
