package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DecisionDefinitionGroupOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class DecisionDefinitionReader {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionReader.class);

  private final Client esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final SessionService sessionService;

  public DecisionDefinitionReader(final Client esClient,
                                  final ConfigurationService configurationService,
                                  final ObjectMapper objectMapper,
                                  final SessionService sessionService) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.sessionService = sessionService;
  }

  public List<DecisionDefinitionOptimizeDto> getDecisionDefinitionsAsService(final boolean withXml) {
    return getDecisionDefinitions(null, withXml);
  }

  public List<DecisionDefinitionOptimizeDto> getDecisionDefinitions(final String userId, final boolean withXml) {
    logger.debug("Fetching decision definitions");
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    String[] fieldsToExclude = withXml ? null : new String[]{DecisionDefinitionType.DECISION_DEFINITION_XML};

    SearchResponse scrollResp = esClient
      .prepareSearch(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setFetchSource(null, fieldsToExclude)
      .setSize(ElasticsearchConstants.LIST_FETCH_LIMIT)
      .get();

    List<DecisionDefinitionOptimizeDto> definitionsResult = ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      DecisionDefinitionOptimizeDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );

    if (userId != null) {
      definitionsResult = filterAuthorizedDecisionDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public Optional<String> getDecisionDefinitionXml(final String decisionDefinitionKey,
                                                   final String decisionDefinitionVersion) {
    return getDecisionDefinition(decisionDefinitionKey, decisionDefinitionVersion)
      .flatMap(
        decisionDefinitionOptimizeDto -> Optional.ofNullable(decisionDefinitionOptimizeDto.getDmn10Xml())
      );
  }

  public List<DecisionDefinitionGroupOptimizeDto> getDecisionDefinitionsGroupedByKey(String userId) {
    Map<String, DecisionDefinitionGroupOptimizeDto> resultMap = getKeyToDecisionDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
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

  private List<DecisionDefinitionOptimizeDto> getDecisionDefinitions(String userId) {
    return this.getDecisionDefinitions(userId, false);
  }

  private List<DecisionDefinitionOptimizeDto> filterAuthorizedDecisionDefinitions(final String userId,
                                                                                  final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    final List<DecisionDefinitionOptimizeDto> result = decisionDefinitions
      .stream()
      .filter(def -> sessionService.isAuthorizedToSeeDecisionDefinition(userId, def.getKey()))
      .collect(Collectors.toList());
    return result;
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
    SearchResponse response = esClient.prepareSearch(
      getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
      .setQuery(
        QueryBuilders.boolQuery()
          .must(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
          .must(termQuery(DECISION_DEFINITION_VERSION, validVersion))
      )
      .setSize(1)
      .get();

    DecisionDefinitionOptimizeDto definitionOptimizeDto = null;
    if (response.getHits().getTotalHits() > 0L) {
      String responseAsString = response.getHits().getAt(0).getSourceAsString();
      definitionOptimizeDto = parseDecisionDefinition(responseAsString);
    }
    return Optional.ofNullable(definitionOptimizeDto);
  }

  private String getLatestVersionToKey(String key) {
    SearchResponse response = esClient
      .prepareSearch(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
      .setTypes(DECISION_DEFINITION_TYPE)
      .setQuery(termQuery(DECISION_DEFINITION_KEY, key))
      .addSort(DECISION_DEFINITION_VERSION, SortOrder.DESC)
      .setSize(1)
      .get();

    if (response.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = response.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(DECISION_DEFINITION_VERSION)) {
        return sourceAsMap.get(DECISION_DEFINITION_VERSION).toString();
      }

    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for decision definition key: " + key);
  }

  private Map<String, DecisionDefinitionGroupOptimizeDto> getKeyToDecisionDefinitionMap(String userId) {
    Map<String, DecisionDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<DecisionDefinitionOptimizeDto> allDefinitions = getDecisionDefinitions(userId);
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
