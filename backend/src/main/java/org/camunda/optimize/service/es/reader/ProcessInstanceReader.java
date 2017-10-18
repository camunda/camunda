package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class ProcessInstanceReader {
  private final Logger logger = LoggerFactory.getLogger(ProcessInstanceReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ConfigurationService configurationService;


  public Set<String> getProcessInstanceIds(int absoluteIndex, String engineAlias) {
    logger.debug("Fetching process instance ids");

    Set<String> result = new HashSet<>();
    QueryBuilder query;
    query = buildBasicQuery(engineAlias);

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getProcessInstanceType())
        .setQuery(query)
        .setFrom(absoluteIndex)
        .setSize(configurationService.getEngineImportVariableInstanceMaxPageSize())
        .setFetchSource("processInstanceId",null)
        .addSort(SortBuilders.fieldSort("startDate").order(SortOrder.ASC))
        .get();

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getSource().get("processInstanceId").toString());
    }

    return result;
  }

  private QueryBuilder buildBasicQuery(String engineAlias) {
    QueryBuilder query;
    if (configurationService.getProcessDefinitionIdsToImport() != null && !configurationService.getProcessDefinitionIdsToImport().isEmpty()) {
      query = QueryBuilders.boolQuery();
      for (String processDefinitionId : configurationService.getProcessDefinitionIdsToImport()) {
        ((BoolQueryBuilder)query)
            .should(QueryBuilders.termQuery("processDefinitionId", processDefinitionId));
      }

    } else {
      query = QueryBuilders.matchAllQuery();
    }
    return query;
  }
}
