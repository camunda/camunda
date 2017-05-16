package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
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

import javax.annotation.PostConstruct;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_ID;

@Component
public class VariableReader {

  private final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  private VariableExtractor variableExtractor;

  @PostConstruct
  public void init() {
    variableExtractor = new VariableExtractor(configurationService);
  }

  public List<GetVariablesResponseDto> getVariables(String processDefinitionId) {
    logger.debug("Fetching variables for process definition: {}", processDefinitionId);
    QueryBuilder query;
    query =
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termsQuery(PROCESS_DEFINITION_ID, processDefinitionId));

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getProcessInstanceType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(configurationService.getMaxVariableValueListSize() + 1) // +1 to see if the limit was exceeded
        .get();

    variableExtractor.clearVariables();
    for (SearchHit hit : scrollResp.getHits().getHits()) {
      variableExtractor.extractVariables(hit);
    }
    return variableExtractor.getVariables();
  }

}
