package org.camunda.optimize.service.es.reader;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.GetVariablesResponseDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.VariableType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class VariableReader {

  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;


  public List<GetVariablesResponseDto> getVariables(String processDefinitionId) {
    logger.debug("Fetching variables for process definition: {}", processDefinitionId);
    QueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(ProcessInstanceType.PROCESS_DEFINITION_ID, processDefinitionId))
        .mustNot(createMustNotMatchVariableTypeQuery("Object"))
        .mustNot(createMustNotMatchVariableTypeQuery("File"))
        .mustNot(createMustNotMatchVariableTypeQuery("Json"))
        .mustNot(createMustNotMatchVariableTypeQuery("Xml"));

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getProcessInstanceType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(configurationService.getMaxVariableValueListSize() + 1) // +1 to see if the limit was exceeded
        .get();

    Map<String, GetVariablesResponseDto> nameToResponse = new HashMap<>();
    for (SearchHit hit : scrollResp.getHits().getHits()) {
      List<Map<String, Object>> variables = (List<Map<String, Object>>) hit.getSource().get("variables");
      extractVariables(nameToResponse, variables);
    }
    return new ArrayList<>(nameToResponse.values());
  }

  private NestedQueryBuilder createMustNotMatchVariableTypeQuery(String variableType) {
    return nestedQuery(
      ProcessInstanceType.VARIABLES,
      termQuery("variables.type", variableType),
      ScoreMode.None
    );
  }

  private void extractVariables(Map<String, GetVariablesResponseDto> nameToResponse, List<Map<String, Object>> variables) {
    for (Map<String, Object> variableInfo : variables) {

      String name = variableInfo.get(ProcessInstanceType.VARIABLE_NAME).toString();
      String type = variableInfo.get(ProcessInstanceType.VARIABLE_TYPE).toString();
      Map<String, Object> valueAsObject = (Map<String, Object>) variableInfo.get("value");
      String key = type.toLowerCase() + "Val";
      String value = valueAsObject.get(key) != null ? valueAsObject.get(key).toString() : null;

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
  }
}
