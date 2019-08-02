/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.TENANT_ID;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToValidVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionReader {

  private OptimizeElasticsearchClient esClient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;

  public Optional<ProcessDefinitionOptimizeDto> getFullyImportedProcessDefinition(
    final String processDefinitionKey,
    final String processDefinitionVersion,
    final String tenantId) {

    if (processDefinitionKey == null || processDefinitionVersion == null) {
      return Optional.empty();
    }

    final String validVersion =
      convertToValidVersion(processDefinitionKey, processDefinitionVersion, this::getLatestVersionToKey);
    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
      .must(termQuery(PROCESS_DEFINITION_VERSION, validVersion))
      .must(existsQuery(PROCESS_DEFINITION_XML));

    if (tenantId != null) {
      query.must(termQuery(TENANT_ID, tenantId));
    } else {
      query.mustNot(existsQuery(TENANT_ID));
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest = new SearchRequest(PROCESS_DEFINITION_TYPE)
      .types(PROCESS_DEFINITION_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch process definition with key [%s], version [%s] and tenantId [%s]",
        processDefinitionKey,
        validVersion,
        tenantId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = null;
    if (searchResponse.getHits().getTotalHits() > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      try {
        processDefinitionOptimizeDto = objectMapper.readValue(responseAsString, ProcessDefinitionOptimizeDto.class);
      } catch (IOException e) {
        log.error("Could not read process definition from Elasticsearch!", e);
      }
    } else {
      log.debug(
        "Could not find process definition xml with key [{}], version [{}] and tenantId [{}]",
        processDefinitionKey,
        validVersion,
        tenantId
      );
    }
    return Optional.ofNullable(processDefinitionOptimizeDto);
  }

  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionByKeyAndEngine(final String processDefinitionKey,
                                                                                   final String engineAlias) {

    if (processDefinitionKey == null) {
      return Optional.empty();
    }

    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
      .must(termQuery(ENGINE, engineAlias));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest = new SearchRequest(PROCESS_DEFINITION_TYPE)
      .types(PROCESS_DEFINITION_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch process definition with key [%s] and engineAlias [%s]", processDefinitionKey, engineAlias
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = null;
    if (searchResponse.getHits().getTotalHits() > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      try {
        processDefinitionOptimizeDto = objectMapper.readValue(responseAsString, ProcessDefinitionOptimizeDto.class);
      } catch (IOException e) {
        log.error("Could not read process definition from Elasticsearch!", e);
      }
    }
    return Optional.ofNullable(processDefinitionOptimizeDto);
  }

  public List<ProcessDefinitionOptimizeDto> getFullyImportedProcessDefinitions(final boolean withXml) {
    final String[] fieldsToExclude = withXml ? null : new String[]{ProcessDefinitionType.PROCESS_DEFINITION_XML};

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.existsQuery(ProcessDefinitionType.PROCESS_DEFINITION_XML))
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(PROCESS_DEFINITION_TYPE)
        .types(PROCESS_DEFINITION_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve process definitions!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve process definitions!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      ProcessDefinitionOptimizeDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public String getLatestVersionToKey(String key) {
    log.debug("Fetching latest process definition for key [{}]", key);

    Script script = createDefaultScript(
      "Integer.parseInt(doc['" + PROCESS_DEFINITION_VERSION + "'].value)",
      Collections.emptyMap()
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(PROCESS_DEFINITION_KEY, key))
      .sort(SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
      .size(1);

    SearchRequest searchRequest = new SearchRequest(PROCESS_DEFINITION_TYPE)
      .types(PROCESS_DEFINITION_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch latest process definition for key [%s]",
        key
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = searchResponse.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(PROCESS_DEFINITION_VERSION)) {
        return sourceAsMap.get(PROCESS_DEFINITION_VERSION).toString();
      }

    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for process definition key: " + key);
  }

}
