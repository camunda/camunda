package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
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

  public List<ProcessDefinitionOptimizeDto> getProcessDefinitions() {
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    SearchResponse sr = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessDefinitionType())
      .setQuery(query)
      .get();

    int numberOfProcessDefinitions = (int) sr.getHits().totalHits();
    List<ProcessDefinitionOptimizeDto> list = new ArrayList<>(numberOfProcessDefinitions);
    for (SearchHit hit : sr.getHits().getHits()) {
      String content = hit.getSourceAsString();
      ProcessDefinitionOptimizeDto processDefinition = null;
      try {
        processDefinition = objectMapper.readValue(content, ProcessDefinitionOptimizeDto.class);
      } catch (IOException e) {
        logger.error("Error while reading process definition from elastic search!", e);
      }
      list.add(processDefinition);
    }
    return list;
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
      logger.warn("Could not find process definition xml with id " + processDefinitionId);
    }
    return xml;
  }


}
