package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
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
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ProcessDefinitionReader {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SessionService sessionService;

  public List<ProcessDefinitionOptimizeDto> getProcessDefinitionsAsService() {
    return this.getProcessDefinitions(null, false);
  }

  private List<ProcessDefinitionOptimizeDto> getProcessDefinitions(String userId) {
    return this.getProcessDefinitions(userId, false);
  }

  public List<ProcessDefinitionOptimizeDto> getProcessDefinitions(String userId, boolean withXml) {
    logger.debug("Fetching process definitions");
    // the front-end needs the xml to work properly. Therefore, we only want to expose definitions
    // where the import is complete including the xml
    QueryBuilder query = QueryBuilders.existsQuery(ProcessDefinitionType.PROCESS_DEFINITION_XML);

    String[] fieldsToExclude = withXml ? null : new String[]{ProcessDefinitionType.PROCESS_DEFINITION_XML};

    SearchResponse scrollResp = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_DEF_TYPE))
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setFetchSource(null, fieldsToExclude)
      .setSize(ElasticsearchConstants.LIST_FETCH_LIMIT)
      .get();

    List<ProcessDefinitionOptimizeDto> definitionsResult = ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      ProcessDefinitionOptimizeDto.class,
      objectMapper,
      esclient,
      configurationService.getElasticsearchScrollTimeout()
    );

    if (userId != null) {
      definitionsResult = filterAuthorizedProcessDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public Optional<String> getProcessDefinitionXml(String userId, String definitionKey, String definitionVersion) {
    return getProcessDefinitionWithXml(definitionKey, definitionVersion)
      .flatMap(processDefinitionOptimizeDto -> {
        if (isAuthorizedToReadProcessDefinition(userId, processDefinitionOptimizeDto)) {
          return Optional.ofNullable(processDefinitionOptimizeDto.getBpmn20Xml());
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the process definition");
        }
      });
  }

  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = getKeyToProcessDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
  }

  public FlowNodeNamesResponseDto getFlowNodeNames(FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto) {
    FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();

    final Optional<ProcessDefinitionOptimizeDto> processDefinitionXmlDto = getProcessDefinitionWithXml(
      flowNodeIdsToNamesRequestDto.getProcessDefinitionKey(),
      flowNodeIdsToNamesRequestDto.getProcessDefinitionVersion()
    );
    if (processDefinitionXmlDto.isPresent()) {
      List<String> nodeIds = flowNodeIdsToNamesRequestDto.getNodeIds();
      if (nodeIds != null && !nodeIds.isEmpty()) {
        for (String id : nodeIds) {
          result.getFlowNodeNames().put(id, processDefinitionXmlDto.get().getFlowNodeNames().get(id));
        }
      } else {
        result.setFlowNodeNames(processDefinitionXmlDto.get().getFlowNodeNames());
      }
    } else {
      logger.debug(
        "No process definition found for key {} and version {}, returning empty result.",
        flowNodeIdsToNamesRequestDto.getProcessDefinitionKey(),
        flowNodeIdsToNamesRequestDto.getProcessDefinitionVersion()
      );
    }

    return result;
  }

  private List<ProcessDefinitionOptimizeDto> filterAuthorizedProcessDefinitions(final String userId,
                                                                                final List<ProcessDefinitionOptimizeDto> processDefinitions) {
    return processDefinitions
      .stream()
      .filter(def -> isAuthorizedToReadProcessDefinition(userId, def))
      .collect(Collectors.toList());
  }

  private boolean isAuthorizedToReadProcessDefinition(final String userId, final ProcessDefinitionOptimizeDto def) {
    return sessionService.isAuthorizedToSeeProcessDefinition(userId, def.getKey());
  }

  private String convertToValidVersion(String processDefinitionKey, String processDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      return getLatestVersionToKey(processDefinitionKey);
    } else {
      return processDefinitionVersion;
    }
  }

  private Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionWithXml(String processDefinitionKey,
                                                                             String processDefinitionVersion) {
    processDefinitionVersion = convertToValidVersion(processDefinitionKey, processDefinitionVersion);
    SearchResponse response = esclient.prepareSearch(
      getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_DEF_TYPE))
      .setQuery(
        QueryBuilders.boolQuery()
          .must(termQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
          .must(termQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion))
      )
      .setSize(1)
      .get();

    ProcessDefinitionOptimizeDto xml = null;
    if (response.getHits().getTotalHits() > 0L) {
      String responseAsString = response.getHits().getAt(0).getSourceAsString();
      try {
        xml = objectMapper.readValue(responseAsString, ProcessDefinitionOptimizeDto.class);
      } catch (IOException e) {
        logger.error("Could not read process definition from Elasticsearch!", e);
      }
    } else {
      logger.warn(
        "Could not find process definition xml with key [{}] and version [{}]",
        processDefinitionKey,
        processDefinitionVersion
      );
    }
    return Optional.ofNullable(xml);
  }

  private String getLatestVersionToKey(String key) {
    SearchResponse response = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_DEF_TYPE))
      .setTypes(ElasticsearchConstants.PROC_DEF_TYPE)
      .setQuery(termQuery(PROCESS_DEFINITION_KEY, key))
      .addSort(PROCESS_DEFINITION_VERSION, SortOrder.DESC)
      .setSize(1)
      .get();

    if (response.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = response.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(PROCESS_DEFINITION_VERSION)) {
        return sourceAsMap.get(PROCESS_DEFINITION_VERSION).toString();
      }

    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for process definition key: " + key);
  }

  private Map<String, ProcessDefinitionGroupOptimizeDto> getKeyToProcessDefinitionMap(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<ProcessDefinitionOptimizeDto> allDefinitions = getProcessDefinitions(userId);
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
