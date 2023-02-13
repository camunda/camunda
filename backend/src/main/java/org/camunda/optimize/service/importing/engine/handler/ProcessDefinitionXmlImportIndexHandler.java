/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
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

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlImportIndexHandler extends DefinitionXmlImportIndexHandler {

  private static final String PROCESS_DEFINITION_XML_IMPORT_INDEX_DOC_ID = "processDefinitionXmlImportIndex";

  private final EngineContext engineContext;

  public ProcessDefinitionXmlImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected Set<String> performSearchQuery() {
    log.debug("Performing process definition search query!");
    Set<String> result = new HashSet<>();
    QueryBuilder query = buildBasicQuery();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .sort(SortBuilders.fieldSort(PROCESS_DEFINITION_ID).order(SortOrder.DESC))
      .size(configurationService.getEngineImportProcessDefinitionXmlMaxPageSize());
    SearchRequest searchRequest = new SearchRequest(PROCESS_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      // refresh to ensure we see the latest state
      esClient.refresh(new RefreshRequest(PROCESS_DEFINITION_INDEX_NAME));
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to search for process definitions!", e);
      throw new OptimizeRuntimeException("Was not able to search for process definitions!", e);
    }

    log.debug("Process definition search query got [{}] results", searchResponse.getHits().getHits().length);

    for (SearchHit hit : searchResponse.getHits().getHits()) {
      result.add(hit.getId());
    }
    return result;
  }

  @Override
  protected String getElasticsearchTypeForStoring() {
    return PROCESS_DEFINITION_XML_IMPORT_INDEX_DOC_ID;
  }

  private QueryBuilder buildBasicQuery() {
    return QueryBuilders.boolQuery()
      .mustNot(existsQuery(PROCESS_DEFINITION_XML))
      .must(termQuery(DEFINITION_DELETED, false))
      .must(termQuery(DATA_SOURCE + "." + DataSourceDto.Fields.type, DataImportSourceType.ENGINE))
      .must(termQuery(DATA_SOURCE + "." + DataSourceDto.Fields.name, engineContext.getEngineAlias()));
  }
}
