package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.GetVariablesResponseDto;
import org.camunda.optimize.service.es.schema.type.VariableType;
import org.camunda.optimize.service.util.ConfigurationService;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
public class VariableReader {

  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;


  public List<GetVariablesResponseDto> getVariables(String processDefinitionId) {
    logger.debug("Fetching variables for process definition: " + processDefinitionId);
    QueryBuilder query;
    query = QueryBuilders.boolQuery()
      .must(QueryBuilders.termsQuery(VariableType.PROCESS_DEFINITION_ID, processDefinitionId))
      .mustNot(QueryBuilders.termsQuery(VariableType.TYPE, "Object"))
      .mustNot(QueryBuilders.termsQuery(VariableType.TYPE, "File"))
      .mustNot(QueryBuilders.termsQuery(VariableType.TYPE, "Json"))
      .mustNot(QueryBuilders.termsQuery(VariableType.TYPE, "Xml"));

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getVariableType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(configurationService.getMaxVariableValueListSize()+1) // +1 to see if the limit was exceeded
        .get();

    Map<String, GetVariablesResponseDto> nameToResponse = new HashMap<>();
    for (SearchHit hit : scrollResp.getHits().getHits()) {
      String name = hit.getSource().get(VariableType.NAME).toString();
      String type = hit.getSource().get(VariableType.TYPE).toString();
      Object valueAsObject = hit.getSource().get(VariableType.VALUE);
      String value = valueAsObject != null ? valueAsObject.toString() : null;

      //Handle the hit...
      if (nameToResponse.containsKey(name)) {
        List<String> values = nameToResponse.get(name).getValues();
        if (values.size() < configurationService.getMaxVariableValueListSize()) {
          values.add(value);
        } else {
          nameToResponse.get(name).setValuesAreComplete(false);
        }
      } else {
        GetVariablesResponseDto responseDto = new GetVariablesResponseDto();
        List<String> values = new LinkedList<>();
        values.add(value);
        responseDto.setValues(values);
        responseDto.setName(name);
        responseDto.setType(type);
        responseDto.setValuesAreComplete(true);
        nameToResponse.put(name, responseDto);
      }
    }
    return new ArrayList<>(nameToResponse.values());
  }
}
