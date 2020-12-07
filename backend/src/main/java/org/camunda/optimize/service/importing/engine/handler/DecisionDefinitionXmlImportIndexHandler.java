/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.ScrollBasedImportIndexHandler;
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

import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.ENGINE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlImportIndexHandler extends ScrollBasedImportIndexHandler {

  private static final String DECISION_DEFINITION_XML_IMPORT_INDEX_DOC_ID = "decisionDefinitionXmlImportIndex";

  private final EngineContext engineContext;

  public DecisionDefinitionXmlImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected Set<String> performScrollQuery() {
    log.debug("Performing scroll search query!");

    final Set<String> result = new HashSet<>();
    SearchResponse scrollResp;
    try {
      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));
      scrollResp = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Could not scroll through decision definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    log.debug("Scroll search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  @Override
  protected Set<String> performInitialSearchQuery() {
    log.debug("Performing initial search query!");
    final Set<String> result = new HashSet<>();
    final QueryBuilder query = buildBasicQuery();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .sort(SortBuilders.fieldSort(DECISION_DEFINITION_ID).order(SortOrder.DESC))
      .size(configurationService.getEngineImportDecisionDefinitionXmlMaxPageSize());
    SearchRequest searchRequest = new SearchRequest(DECISION_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    SearchResponse scrollResp;
    try {
      // refresh to ensure we see the latest state
      esClient.refresh(new RefreshRequest(DECISION_DEFINITION_INDEX_NAME));
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to scroll for decision definitions!", e);
      throw new OptimizeRuntimeException("Was not able to scroll for decision definitions!", e);
    }

    log.debug("Initial search query got [{}] results", scrollResp.getHits().getHits().length);

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

  private QueryBuilder buildBasicQuery() {
    return QueryBuilders.boolQuery()
      .mustNot(existsQuery(DecisionDefinitionIndex.DECISION_DEFINITION_XML))
      .must(termQuery(ENGINE, engineContext.getEngineAlias()));
  }
}
