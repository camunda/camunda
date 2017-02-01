package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.service.util.ConfigurationService;
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

  public List<ProcessDefinitionDto> getProcessDefinitions() {
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    SearchResponse sr = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessDefinitionType())
      .setQuery(query)
      .get();

    int numberOfProcessDefinitions = (int) sr.getHits().totalHits();
    List<ProcessDefinitionDto> list = new ArrayList<>(numberOfProcessDefinitions);
    for (SearchHit hit : sr.getHits().getHits()) {
      String content = hit.getSourceAsString();
      ProcessDefinitionDto processDefinition = null;
      try {
        processDefinition = objectMapper.readValue(content, ProcessDefinitionDto.class);
      } catch (IOException e) {
        logger.error("Error while reading process definition from elastic search!", e);
      }
      list.add(processDefinition);
    }
    return list;
  }

  public String getProcessDefinitionXmls(String processDefinitionId) {
    QueryBuilder query;
    query = QueryBuilders
      .idsQuery(configurationService.getProcessDefinitionXmlType())
      .addIds(processDefinitionId);

    SearchResponse sr = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessDefinitionXmlType())
      .setQuery(query)
      .get();

    String xml = null;
    if(sr.getHits().getTotalHits() > 0L) {
      SearchHit hit = sr.getHits().getAt(0);
      xml = hit.getSource().get("bpmn20Xml").toString();
    }
    return xml;
  }


}
