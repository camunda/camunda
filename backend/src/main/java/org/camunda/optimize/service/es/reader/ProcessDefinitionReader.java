package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
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

@Component
public class ProcessDefinitionReader {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  @Autowired
  private TransportClient esclient;

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

    ArrayList <String> types = new ArrayList<>();
    types.add(configurationService.getProcessDefinitionType());
    if (withXml) {
      types.add(configurationService.getProcessDefinitionXmlType());
    }

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(types.toArray(new String[types.size()] ))
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(100)
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
    definitionsResult.put(mapped.getId(),mapped);
  }

  private void addPartialDefinition(HashMap<String, ExtendedProcessDefinitionOptimizeDto> definitionsResult, SearchHit hit) {
    String id = hit.getSource().get("id").toString();
    String xml = hit.getSource().get("bpmn20Xml").toString();
    if (definitionsResult.containsKey(id)) {
      definitionsResult.get(id).setBpmn20Xml(xml);
    } else {
      ExtendedProcessDefinitionOptimizeDto toAdd = new ExtendedProcessDefinitionOptimizeDto();
      toAdd.setId(id);
      toAdd.setBpmn20Xml(xml);
      definitionsResult.put(toAdd.getId(),toAdd);
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
    if(response.isExists()) {
      xml = response.getSource().get("bpmn20Xml").toString();
    }else {
      logger.warn("Could not find process definition xml with id {}", processDefinitionId);
    }
    return xml;
  }


}
