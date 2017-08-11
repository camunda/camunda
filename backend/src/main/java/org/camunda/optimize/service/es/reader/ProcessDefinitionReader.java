package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
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
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(types.toArray(new String[types.size()]))
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
    String id = hit.getSource().get(ProcessDefinitionType.PROCESS_DEFINITION_ID).toString();
    String xml = hit.getSource().get(ProcessDefinitionXmlType.BPMN_20_XML).toString();
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

  public String getProcessDefinitionXml(String processDefinitionId) {
    GetResponse response = esclient.prepareGet(
        configurationService.getOptimizeIndex(),
        configurationService.getProcessDefinitionXmlType(),
        processDefinitionId)
        .get();

    String xml = null;
    if (response.isExists()) {
      xml = response.getSource().get(ProcessDefinitionXmlType.BPMN_20_XML).toString();
    } else {
      logger.warn("Could not find process definition xml with id {}", processDefinitionId);
    }
    return xml;
  }

  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey() {
    Map<String, ProcessDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    List<ExtendedProcessDefinitionOptimizeDto> allDefinitions = getProcessDefinitions();
    for (ExtendedProcessDefinitionOptimizeDto process : allDefinitions) {
      if (!resultMap.containsKey(process.getKey())) {
        resultMap.put(process.getKey(), constructGroup(process));
      }
      resultMap.get(process.getKey()).getVersions().add(process);
    }
    return new ArrayList<>(resultMap.values());
  }

  private ProcessDefinitionGroupOptimizeDto constructGroup(ExtendedProcessDefinitionOptimizeDto process) {
    ProcessDefinitionGroupOptimizeDto result = new ProcessDefinitionGroupOptimizeDto();
    result.setKey(process.getKey());
    return result;
  }

  public Map<String, String> getProcessDefinitionsXml(List<String> ids) {
    Map<String, String> result = new HashMap<>();
    SearchResponse scrollResp = esclient.prepareSearch(
        configurationService.getOptimizeIndex())
        .setTypes(configurationService.getProcessDefinitionXmlType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(QueryBuilders.termsQuery(ProcessDefinitionXmlType.ID, ids))
        .setSize(100)
        .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        result.put(
            hit.getSource().get(ProcessDefinitionXmlType.ID).toString(),
            hit.getSource().get(ProcessDefinitionXmlType.BPMN_20_XML).toString()
        );
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return result;
  }
}
