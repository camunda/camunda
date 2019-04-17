/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
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
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ProcessDefinitionReader {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  private RestHighLevelClient esClient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;
  private DefinitionAuthorizationService authorizationService;

  @Autowired
  public ProcessDefinitionReader(RestHighLevelClient esClient, ConfigurationService configurationService,
                                 ObjectMapper objectMapper, DefinitionAuthorizationService authorizationService) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.authorizationService = authorizationService;
  }

  public List<ProcessDefinitionOptimizeDto> fetchFullyImportedProcessDefinitionsAsService() {
    return this.fetchFullyImportedProcessDefinitions(null);
  }

  private List<ProcessDefinitionOptimizeDto> fetchFullyImportedProcessDefinitions(final String userId) {
    return this.fetchFullyImportedProcessDefinitions(userId, false);
  }

  public List<ProcessDefinitionOptimizeDto> fetchFullyImportedProcessDefinitions(final String userId, boolean withXml) {
    logger.debug("Fetching process definitions");
    // the front-end needs the xml to work properly. Therefore, we only want to expose definitions
    // where the import is complete including the xml
    QueryBuilder query = QueryBuilders.existsQuery(ProcessDefinitionType.PROCESS_DEFINITION_XML);

    List<ProcessDefinitionOptimizeDto> definitionsResult = fetchProcessDefinitions(withXml, query);

    if (userId != null) {
      definitionsResult = filterAuthorizedProcessDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public Optional<ProcessDefinitionOptimizeDto> getFullyImportedProcessDefinitionAsService(
    final String processDefinitionKey,
    final String processDefinitionVersion) {

    if (processDefinitionKey == null || processDefinitionVersion == null) {
      return Optional.empty();
    }

    String validVersion = convertToValidVersion(processDefinitionKey, processDefinitionVersion);
    QueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
      .must(termQuery(PROCESS_DEFINITION_VERSION, validVersion))
      .must(existsQuery(PROCESS_DEFINITION_XML));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_DEF_TYPE))
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch process definition with key [%s] and version [%s]",
        processDefinitionKey,
        processDefinitionVersion
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    ProcessDefinitionOptimizeDto xml = null;
    if (searchResponse.getHits().getTotalHits() > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      try {
        xml = objectMapper.readValue(responseAsString, ProcessDefinitionOptimizeDto.class);
      } catch (IOException e) {
        logger.error("Could not read process definition from Elasticsearch!", e);
      }
    } else {
      logger.debug(
        "Could not find process definition xml with key [{}] and version [{}]",
        processDefinitionKey,
        processDefinitionVersion
      );
    }
    return Optional.ofNullable(xml);
  }

  public Optional<String> getProcessDefinitionXml(String userId, String definitionKey, String definitionVersion) {
    return getFullyImportedProcessDefinitionAsService(definitionKey, definitionVersion)
      .map(processDefinitionOptimizeDto -> {
        if (isAuthorizedToReadProcessDefinition(userId, processDefinitionOptimizeDto)) {
          return processDefinitionOptimizeDto.getBpmn20Xml();
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the process definition");
        }
      });
  }

  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = getKeyToProcessDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
  }

  private List<ProcessDefinitionOptimizeDto> filterAuthorizedProcessDefinitions(final String userId,
                                                                                final List<ProcessDefinitionOptimizeDto> processDefinitions) {
    return processDefinitions
      .stream()
      .filter(def -> isAuthorizedToReadProcessDefinition(userId, def))
      .collect(Collectors.toList());
  }

  private List<ProcessDefinitionOptimizeDto> fetchProcessDefinitions(final boolean withXml, final QueryBuilder query) {
    final String[] fieldsToExclude = withXml ? null : new String[]{ProcessDefinitionType.PROCESS_DEFINITION_XML};

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_DEF_TYPE))
        .types(PROC_DEF_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Was not able to retrieve process definitions!", e);
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

  private boolean isAuthorizedToReadProcessDefinition(final String userId, final ProcessDefinitionOptimizeDto def) {
    return authorizationService.isAuthorizedToSeeProcessDefinition(userId, def.getKey());
  }

  private String convertToValidVersion(String processDefinitionKey, String processDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      return getLatestVersionToKey(processDefinitionKey);
    } else {
      return processDefinitionVersion;
    }
  }

  private String getLatestVersionToKey(String key) {
    logger.debug("Fetching latest process definition for key [{}]", key);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(PROCESS_DEFINITION_KEY, key))
      .sort(PROCESS_DEFINITION_VERSION, SortOrder.DESC)
      .size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_DEF_TYPE))
        .types(PROC_DEF_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch latest process definition for key [%s]",
        key
      );
      logger.error(reason, e);
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

  private Map<String, ProcessDefinitionGroupOptimizeDto> getKeyToProcessDefinitionMap(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<ProcessDefinitionOptimizeDto> allDefinitions = fetchFullyImportedProcessDefinitions(userId);
    for (ProcessDefinitionOptimizeDto process : allDefinitions) {
      String key = process.getKey();
      if (!resultMap.containsKey(key)) {
        resultMap.put(key, constructGroup(process));
      }
      resultMap.get(key).getVersions().add(process);
    }
    resultMap.values().forEach(ProcessDefinitionGroupOptimizeDto::sort);
    return resultMap;
  }


  private ProcessDefinitionGroupOptimizeDto constructGroup(ProcessDefinitionOptimizeDto process) {
    ProcessDefinitionGroupOptimizeDto result = new ProcessDefinitionGroupOptimizeDto();
    result.setKey(process.getKey());
    return result;
  }
}
