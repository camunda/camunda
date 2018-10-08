package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

  private List<ProcessDefinitionOptimizeDto> getProcessDefinitions(String userId) {
    return this.getProcessDefinitions(userId, false);
  }

  public List<ProcessDefinitionOptimizeDto> getProcessDefinitions(String userId,
                                                                  boolean withXml) {
    logger.debug("Fetching process definitions");
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    ArrayList<String> types = new ArrayList<>();
    types.add(configurationService.getProcessDefinitionType());

    String[] fieldsToExclude = withXml? null: new String[]{ProcessDefinitionType.PROCESS_DEFINITION_XML};

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(types))
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setFetchSource(null, fieldsToExclude)
        .setSize(1000)
        .get();

    List<ProcessDefinitionOptimizeDto> definitionsResult = new ArrayList<>();
    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        try {
          ProcessDefinitionOptimizeDto definition =
            objectMapper.readValue(hit.getSourceAsString(), ProcessDefinitionOptimizeDto.class);
          definitionsResult.add(definition);
        } catch (IOException e) {
          logger.error("Error while reading process definition from elastic search!", e);
        }
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    definitionsResult = filterAuthorizedProcessDefinitions(userId, definitionsResult);
    return definitionsResult;
  }

  private List<ProcessDefinitionOptimizeDto>
                filterAuthorizedProcessDefinitions(String userId,
                                                   List<ProcessDefinitionOptimizeDto> result) {
    result = result
      .stream()
      .filter(def -> sessionService.isAuthorizedToSeeDefinition(userId, def.getKey()))
      .collect(Collectors.toList());
    return result;
  }

  public String getProcessDefinitionXml(String processDefinitionKey, String processDefinitionVersion) {
    ProcessDefinitionOptimizeDto processDefinitionXmlDto =
      getProcessDefinitionWithXml(processDefinitionKey, processDefinitionVersion);
    if( processDefinitionXmlDto != null && processDefinitionXmlDto.getBpmn20Xml() != null ){
      return processDefinitionXmlDto.getBpmn20Xml();
    } else {
      String notFoundErrorMessage = "Could not find xml for process definition with key [" + processDefinitionKey +
        "] and version [" + processDefinitionVersion + "]. It is possible that is hasn't been imported yet.";
      logger.error(notFoundErrorMessage);
      throw new NotFoundException(notFoundErrorMessage);
    }
  }

  private String convertToValidVersion(String processDefinitionKey, String processDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      return getLatestVersionToKey(processDefinitionKey);
    } else {
      return processDefinitionVersion;
    }
  }

  private ProcessDefinitionOptimizeDto getProcessDefinitionWithXml(String processDefinitionKey, String processDefinitionVersion) {
    processDefinitionVersion = convertToValidVersion(processDefinitionKey, processDefinitionVersion);
    SearchResponse response = esclient.prepareSearch(
        configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()))
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
      logger.warn("Could not find process definition xml with key [{}] and version [{}]",
        processDefinitionKey,
        processDefinitionVersion
      );
    }
    return xml;
  }

  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey(String userId) {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = getKeyToProcessDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
  }

  private String getLatestVersionToKey(String key) {
    SearchResponse response = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()))
        .setTypes(configurationService.getProcessDefinitionType())
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

  public FlowNodeNamesResponseDto getFlowNodeNames(FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto) {
    FlowNodeNamesResponseDto result = new FlowNodeNamesResponseDto();

    ProcessDefinitionOptimizeDto processDefinitionXmlDto = getProcessDefinitionWithXml(
        flowNodeIdsToNamesRequestDto.getProcessDefinitionKey(),
        flowNodeIdsToNamesRequestDto.getProcessDefinitionVersion()
    );
    if (processDefinitionXmlDto != null) {
      List<String> nodeIds = flowNodeIdsToNamesRequestDto.getNodeIds();
      if (nodeIds != null && !nodeIds.isEmpty()) {
        for (String id : nodeIds) {
          result.getFlowNodeNames().put(id, processDefinitionXmlDto.getFlowNodeNames().get(id));
        }
      } else {
        result.setFlowNodeNames(processDefinitionXmlDto.getFlowNodeNames());
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
}
