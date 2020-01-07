/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToValidDefinitionVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionReader {
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;

  public Optional<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinition(
    final String decisionDefinitionKey,
    final List<String> decisionDefinitionVersions,
    final String tenantId) {

    if (decisionDefinitionKey == null || decisionDefinitionVersions == null || decisionDefinitionVersions.isEmpty()) {
      return Optional.empty();
    }

    final String validVersion = convertToValidDefinitionVersion(
      decisionDefinitionKey,
      decisionDefinitionVersions,
      this::getLatestVersionToKey
    );
    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
      .must(termQuery(DECISION_DEFINITION_VERSION, validVersion))
      .must(existsQuery(DECISION_DEFINITION_XML));

    if (tenantId != null) {
      query.must(termQuery(TENANT_ID, tenantId));
    } else {
      query.mustNot(existsQuery(TENANT_ID));
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest = new SearchRequest(DECISION_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch decision definition with key [%s], version [%s] and tenantId [%s]",
        decisionDefinitionKey,
        validVersion,
        tenantId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    DecisionDefinitionOptimizeDto definitionOptimizeDto = null;
    if (searchResponse.getHits().getTotalHits().value > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      definitionOptimizeDto = parseDecisionDefinition(responseAsString);
    } else {
      log.debug(
        "Could not find decision definition xml with key [{}], version [{}] and tenantId [{}]",
        decisionDefinitionKey,
        validVersion,
        tenantId
      );
    }
    return Optional.ofNullable(definitionOptimizeDto);
  }

  public Optional<DecisionDefinitionOptimizeDto> getDecisionDefinitionByKeyAndEngineOmitXml(final String decisionDefinitionKey,
                                                                                            final String engineAlias) {

    if (decisionDefinitionKey == null) {
      return Optional.empty();
    }

    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
      .must(termQuery(ENGINE, engineAlias));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(1)
      .fetchSource(null, DECISION_DEFINITION_XML);
    SearchRequest searchRequest = new SearchRequest(DECISION_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch decision definition with key [%s] and engine [%s]", decisionDefinitionKey, engineAlias
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    DecisionDefinitionOptimizeDto definitionOptimizeDto = null;
    if (searchResponse.getHits().getTotalHits().value > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      definitionOptimizeDto = parseDecisionDefinition(responseAsString);
    }
    return Optional.ofNullable(definitionOptimizeDto);
  }

  public List<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinitionsForKeys(final boolean withXml,
                                                                                        final Set<String> keys) {

    if (keys.isEmpty()) {
      return Collections.emptyList();
    }

    final BoolQueryBuilder query = boolQuery().must(termsQuery(PROCESS_DEFINITION_KEY, keys));
    return fetchDecisionDefinitions(true, withXml, query);
  }

  public List<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinitions(final boolean withXml) {
    return getDecisionDefinitions(true, withXml);
  }

  public List<DecisionDefinitionOptimizeDto> getDecisionDefinitions(final boolean fullyImported,
                                                                    final boolean withXml) {
    return fetchDecisionDefinitions(fullyImported, withXml, matchAllQuery());
  }

  private DecisionDefinitionOptimizeDto parseDecisionDefinition(final String responseAsString) {
    final DecisionDefinitionOptimizeDto definitionOptimizeDto;
    try {
      definitionOptimizeDto = objectMapper.readValue(responseAsString, DecisionDefinitionOptimizeDto.class);
    } catch (IOException e) {
      log.error("Could not read decision definition from Elasticsearch!", e);
      throw new OptimizeRuntimeException("Failure reading decision definition", e);
    }
    return definitionOptimizeDto;
  }

  public String getLatestVersionToKey(String key) {
    log.debug("Fetching latest decision definition for key [{}]", key);

    Script script = createDefaultScript(
      "Integer.parseInt(doc['" + DECISION_DEFINITION_VERSION + "'].value)",
      Collections.emptyMap()
    );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(DECISION_DEFINITION_KEY, key))
      .sort(SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
      .size(1);

    SearchRequest searchRequest = new SearchRequest(DECISION_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch latest decision definition for key [%s]",
        key
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = searchResponse.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(DECISION_DEFINITION_VERSION)) {
        return sourceAsMap.get(DECISION_DEFINITION_VERSION).toString();
      }

    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for decision definition key: " + key);
  }

  private List<DecisionDefinitionOptimizeDto> fetchDecisionDefinitions(final boolean fullyImported,
                                                                       final boolean withXml,
                                                                       final QueryBuilder query) {
    final BoolQueryBuilder rootQuery = boolQuery().must(
      fullyImported ? existsQuery(DECISION_DEFINITION_XML) : matchAllQuery()
    );
    rootQuery.must(query);
    final String[] fieldsToExclude = withXml ? null : new String[]{DECISION_DEFINITION_XML};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(rootQuery)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(DECISION_DEFINITION_INDEX_NAME)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve decision definitions!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve decision definitions!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      DecisionDefinitionOptimizeDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

}
