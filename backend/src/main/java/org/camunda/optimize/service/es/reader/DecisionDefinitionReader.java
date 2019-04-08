/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DecisionDefinitionGroupOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class DecisionDefinitionReader {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionReader.class);

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final DefinitionAuthorizationService authorizationService;
  private final RestHighLevelClient esClient;

  @Autowired
  public DecisionDefinitionReader(final ConfigurationService configurationService,
                                  final ObjectMapper objectMapper,
                                  final DefinitionAuthorizationService authorizationService,
                                  final RestHighLevelClient esClient) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.authorizationService = authorizationService;
    this.esClient = esClient;
  }

  public List<DecisionDefinitionOptimizeDto> fetchFullyImportedDecisionDefinitionsAsService() {
    return fetchFullyImportedDecisionDefinitionsAsService(false);
  }

  public List<DecisionDefinitionOptimizeDto> fetchFullyImportedDecisionDefinitionsAsService(final boolean withXml) {
    return fetchFullyImportedDecisionDefinitions(null, withXml);
  }

  public List<DecisionDefinitionOptimizeDto> fetchFullyImportedDecisionDefinitions(final String userId,
                                                                                   final boolean withXml) {
    logger.debug("Fetching decision definitions");
    // the front-end needs the xml to work properly. Therefore, we only want to expose definitions
    // where the import is complete including the xml
    final QueryBuilder query = QueryBuilders.existsQuery(DecisionDefinitionType.DECISION_DEFINITION_XML);

    List<DecisionDefinitionOptimizeDto> definitionsResult = fetchDecisionDefinitions(withXml, query);

    if (userId != null) {
      definitionsResult = filterAuthorizedDecisionDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  /**
   * This function retrieves all decision definitions independent of if the respective xml was already imported or not.
   */
  public List<DecisionDefinitionOptimizeDto> fetchAllDecisionDefinitionWithoutXmlAsService() {
    logger.debug("Fetching all decision definitions including those where the xml hasn't been fetched yet.");
    final QueryBuilder query = QueryBuilders.matchAllQuery();
    return fetchDecisionDefinitions(false, query);
  }

  public Optional<String> getDecisionDefinitionXml(final String userId,
                                                   final String definitionKey,
                                                   final String definitionVersion) {
    return getDecisionDefinition(definitionKey, definitionVersion)
      .flatMap(definitionOptimizeDto -> {
        if (isAuthorizedToSeeDecisionDefinition(userId, definitionOptimizeDto)) {
          return Optional.ofNullable(definitionOptimizeDto.getDmn10Xml());
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the decision definition");
        }
      });
  }

  public List<DecisionDefinitionGroupOptimizeDto> getDecisionDefinitionsGroupedByKey(String userId) {
    Map<String, DecisionDefinitionGroupOptimizeDto> resultMap = getKeyToDecisionDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
  }

  private List<DecisionDefinitionOptimizeDto> fetchDecisionDefinitions(final boolean withXml,
                                                                       final QueryBuilder query) {
    final String[] fieldsToExclude = withXml ? null : new String[]{DecisionDefinitionType.DECISION_DEFINITION_XML};

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
        .types(DECISION_DEFINITION_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Was not able to retrieve decision definitions!", e);
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

  private DecisionDefinitionOptimizeDto parseDecisionDefinition(final String responseAsString) {
    final DecisionDefinitionOptimizeDto definitionOptimizeDto;
    try {
      definitionOptimizeDto = objectMapper.readValue(responseAsString, DecisionDefinitionOptimizeDto.class);
    } catch (IOException e) {
      logger.error("Could not read decision definition from Elasticsearch!", e);
      throw new OptimizeRuntimeException("Failure reading decision definition", e);
    }
    return definitionOptimizeDto;
  }

  private List<DecisionDefinitionOptimizeDto> fetchFullyImportedDecisionDefinitions(String userId) {
    return this.fetchFullyImportedDecisionDefinitions(userId, false);
  }

  private List<DecisionDefinitionOptimizeDto> filterAuthorizedDecisionDefinitions(final String userId,
                                                                                  final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    return decisionDefinitions
      .stream()
      .filter(def -> isAuthorizedToSeeDecisionDefinition(userId, def))
      .collect(Collectors.toList());
  }

  private boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                      final DecisionDefinitionOptimizeDto definitionOptimizeDto) {
    return authorizationService.isAuthorizedToSeeDecisionDefinition(userId, definitionOptimizeDto.getKey());
  }

  private String convertToValidVersion(String decisionDefinitionKey, String decisionDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(decisionDefinitionVersion)) {
      return getLatestVersionToKey(decisionDefinitionKey);
    } else {
      return decisionDefinitionVersion;
    }
  }

  private Optional<DecisionDefinitionOptimizeDto> getDecisionDefinition(final String decisionDefinitionKey,
                                                                        final String decisionDefinitionVersion) {
    String validVersion = convertToValidVersion(decisionDefinitionKey, decisionDefinitionVersion);
    QueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
      .must(termQuery(DECISION_DEFINITION_VERSION, validVersion));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch decision definition with key [%s] and version [%s]",
        decisionDefinitionKey,
        decisionDefinitionVersion
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    DecisionDefinitionOptimizeDto definitionOptimizeDto = null;
    if (searchResponse.getHits().getTotalHits() > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      definitionOptimizeDto = parseDecisionDefinition(responseAsString);
    }
    return Optional.ofNullable(definitionOptimizeDto);
  }

  private String getLatestVersionToKey(String key) {
    logger.debug("Fetching latest decision definition for key [{}]", key);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(DECISION_DEFINITION_KEY, key))
      .sort(DECISION_DEFINITION_VERSION, SortOrder.DESC)
      .size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
        .types(DECISION_DEFINITION_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch latest decision definition for key [%s]",
        key
      );
      logger.error(reason, e);
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

  private Map<String, DecisionDefinitionGroupOptimizeDto> getKeyToDecisionDefinitionMap(String userId) {
    Map<String, DecisionDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<DecisionDefinitionOptimizeDto> allDefinitions = fetchFullyImportedDecisionDefinitions(userId);
    for (DecisionDefinitionOptimizeDto decisionDefinition : allDefinitions) {
      String key = decisionDefinition.getKey();
      if (!resultMap.containsKey(key)) {
        resultMap.put(key, constructGroup(decisionDefinition));
      }
      resultMap.get(key).getVersions().add(decisionDefinition);
    }
    resultMap.values().forEach(DecisionDefinitionGroupOptimizeDto::sort);
    return resultMap;
  }


  private DecisionDefinitionGroupOptimizeDto constructGroup(DecisionDefinitionOptimizeDto definitionOptimizeDto) {
    DecisionDefinitionGroupOptimizeDto result = new DecisionDefinitionGroupOptimizeDto();
    result.setKey(definitionOptimizeDto.getKey());
    return result;
  }

}
