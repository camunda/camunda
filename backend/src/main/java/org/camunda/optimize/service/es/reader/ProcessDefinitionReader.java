package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESSS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESS_DEFINITION_VERSION;
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

  public List<ExtendedProcessDefinitionOptimizeDto> getProcessDefinitions() {
    return this.getProcessDefinitions(false);
  }

  public List<ExtendedProcessDefinitionOptimizeDto> getProcessDefinitions(boolean withXml) {
    logger.debug("Fetching process definitions");
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    ArrayList<String> types = new ArrayList<>();
    types.add(configurationService.getProcessDefinitionType());
    if (withXml) {
      types.add(configurationService.getProcessDefinitionXmlType());
    }

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(types))
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult = new HashMap<>();
    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        if (configurationService.getProcessDefinitionType().equals(hit.getType())) {
          addFullDefinition(definitionsResult, hit);
        } else if (configurationService.getProcessDefinitionXmlType().equals(hit.getType())) {
          addPartialDefinition(definitionsResult, hit);
        } else {
          throw new OptimizeRuntimeException("Unknown type returned as process definition");
        }
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return new ArrayList<>(definitionsResult.values());
  }

  private void addFullDefinition(HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult, SearchHit hit) {
    ExtendedProcessDefinitionOptimizeDto mapped = mapSearchToProcessDefinition(hit);
    if (definitionsResult.containsKey(mapped.getId())) {
      mapped.setBpmn20Xml(definitionsResult.get(mapped.getId()).getBpmn20Xml());
    }
    definitionsResult.put(mapped.getId(), mapped);
  }

  private void addPartialDefinition(HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult, SearchHit hit) {
    String id = hit.getSourceAsMap().get(ProcessDefinitionXmlType.PROCESSS_DEFINITION_ID).toString();
    String xml = hit.getSourceAsMap().get(ProcessDefinitionXmlType.BPMN_20_XML).toString();
    if (definitionsResult.containsKey(id)) {
      definitionsResult.get(id).setBpmn20Xml(xml);
    } else {
      ExtendedProcessDefinitionOptimizeDto toAdd = new ExtendedProcessDefinitionOptimizeDto();
      toAdd.setId(id);
      toAdd.setBpmn20Xml(xml);
      definitionsResult.put(toAdd.getId(), toAdd);
    }
  }

  private ExtendedProcessDefinitionOptimizeDto mapSearchToProcessDefinition(SearchHit hit) {
    String content = hit.getSourceAsString();
    ExtendedProcessDefinitionOptimizeDto processDefinition = null;
    try {
      processDefinition = objectMapper.readValue(content, ExtendedProcessDefinitionOptimizeDto.class);
    } catch (IOException e) {
      logger.error("Error while reading process definition from elastic search!", e);
    }
    return processDefinition;
  }

  public String getProcessDefinitionXml(String processDefinitionKey, String processDefinitionVersion) {
    processDefinitionVersion = convertToValidVersion(processDefinitionKey, processDefinitionVersion);
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = getProcessDefinitionXmlDto(processDefinitionKey, processDefinitionVersion);
    return processDefinitionXmlDto == null ? null : processDefinitionXmlDto.getBpmn20Xml();
  }

  private String convertToValidVersion(String processDefinitionKey, String processDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(processDefinitionVersion)) {
      return getLatestVersionToKey(processDefinitionKey);
    } else {
      return processDefinitionVersion;
    }
  }

  private ProcessDefinitionXmlOptimizeDto getProcessDefinitionXmlDto(String processDefinitionKey, String processDefinitionVersion) {
    SearchResponse response = esclient.prepareSearch(
        configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()))
        .setQuery(
          QueryBuilders.boolQuery()
            .must(termQuery(PROCESS_DEFINITION_KEY, processDefinitionKey))
            .must(termQuery(PROCESS_DEFINITION_VERSION, processDefinitionVersion))
        )
        .setSize(1)
        .get();

    ProcessDefinitionXmlOptimizeDto xml = null;
    if (response.getHits().getTotalHits() > 0L) {
      xml = getProcessDefinitionXmlOptimizeDto(response.getHits().getAt(0));
    } else {
      logger.warn("Could not find process definition xml with key [{}] and version [{}]",
        processDefinitionKey,
        processDefinitionVersion
      );
    }
    return xml;
  }

  public String getProcessDefinitionXml(String processDefinitionId) {
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = getProcessDefinitionXmlDto(processDefinitionId);
    return processDefinitionXmlDto == null ? null : processDefinitionXmlDto.getBpmn20Xml();
  }

  private ProcessDefinitionXmlOptimizeDto getProcessDefinitionXmlDto(String processDefinitionId) {
    SearchResponse response = esclient.prepareSearch(
        configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()))
        .setQuery(
          QueryBuilders.boolQuery()
            .must(termQuery(PROCESSS_DEFINITION_ID, processDefinitionId))
        )
        .setSize(1)
        .get();

    ProcessDefinitionXmlOptimizeDto xml = null;
    if (response.getHits().getTotalHits() > 0L) {
      xml = getProcessDefinitionXmlOptimizeDto(response.getHits().getAt(0));
    } else {
      logger.warn("Could not find process definition xml with id [{}]",
        processDefinitionId
      );
    }
    return xml;
  }

  public static ProcessDefinitionXmlOptimizeDto getProcessDefinitionXmlOptimizeDto(SearchHit response) {
    ProcessDefinitionXmlOptimizeDto xml;
    xml = new ProcessDefinitionXmlOptimizeDto ();

    Map<String, Object> xmlResponse = response.getSourceAsMap();
    xml.setBpmn20Xml(xmlResponse.get(ProcessDefinitionXmlType.BPMN_20_XML).toString());
    xml.setProcessDefinitionId(xmlResponse.get(ProcessDefinitionXmlType.PROCESSS_DEFINITION_ID).toString());
    if (xmlResponse.get(ProcessDefinitionXmlType.ENGINE) != null) {
      xml.setEngine(xmlResponse.get(ProcessDefinitionXmlType.ENGINE).toString());
    }
    xml.setFlowNodeNames((Map<String, String>) xmlResponse.get(ProcessDefinitionXmlType.FLOW_NODE_NAMES));
    return xml;
  }


  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey() {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = getKeyToProcessDefinitionMap();
    return new ArrayList<>(resultMap.values());
  }

  public String getLatestVersionToKey(String key) {
    Map<String, ProcessDefinitionGroupOptimizeDto> keyToVersionsMap = getKeyToProcessDefinitionMap();
    if (keyToVersionsMap.containsKey(key)) {
      List<ExtendedProcessDefinitionOptimizeDto> versions = keyToVersionsMap.get(key).getVersions();
      if (versions != null && !versions.isEmpty()) {
        return versions.get(0).getVersion().toString();
      } else {
        throw new OptimizeRuntimeException("Unable to retrieve latest version for process definition key: " + key);
      }
    } else {
      throw new OptimizeRuntimeException("Unable to retrieve latest version for process definition key: " + key);
    }
  }

  private Map<String, ProcessDefinitionGroupOptimizeDto> getKeyToProcessDefinitionMap() {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<ExtendedProcessDefinitionOptimizeDto> allDefinitions = getProcessDefinitions();
    for (ExtendedProcessDefinitionOptimizeDto process : allDefinitions) {
      String key = process.getKey();
      if (!resultMap.containsKey(key)) {
        resultMap.put(key, constructGroup(process));
      }
      resultMap.get(key).getVersions().add(process);
    }
    resultMap.values().forEach(ProcessDefinitionGroupOptimizeDto::sort);
    return resultMap;
  }


  private ProcessDefinitionGroupOptimizeDto constructGroup(ExtendedProcessDefinitionOptimizeDto process) {
    ProcessDefinitionGroupOptimizeDto result = new ProcessDefinitionGroupOptimizeDto();
    result.setKey(process.getKey());
    return result;
  }

  public Map<String, String> getProcessDefinitionsXml(List<String> ids) {
    Map<String, String> result = new HashMap<>();
    SearchResponse scrollResp = esclient.prepareSearch(
        configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()))
        .setTypes(configurationService.getProcessDefinitionXmlType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(QueryBuilders.termsQuery(PROCESSS_DEFINITION_ID, ids))
        .setSize(100)
        .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        result.put(
            hit.getSourceAsMap().get(PROCESSS_DEFINITION_ID).toString(),
            hit.getSourceAsMap().get(ProcessDefinitionXmlType.BPMN_20_XML).toString()
        );
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return result;
  }

  public FlowNodeNamesDto getFlowNodeNames(String processDefinitionId, List<String> nodeIds) {
    FlowNodeNamesDto result = new FlowNodeNamesDto();
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = getProcessDefinitionXmlDto(processDefinitionId);
    if (nodeIds != null && !nodeIds.isEmpty()) {
      for (String id : nodeIds) {
        result.getFlowNodeNames().put(id, processDefinitionXmlDto.getFlowNodeNames().get(id));
      }
    } else {
      result.setFlowNodeNames(processDefinitionXmlDto.getFlowNodeNames());
    }
    return result;
  }
}
