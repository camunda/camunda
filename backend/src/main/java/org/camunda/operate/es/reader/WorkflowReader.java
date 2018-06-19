package org.camunda.operate.es.reader;

import static org.camunda.operate.es.types.WorkflowType.BPMN_XML;
import static org.camunda.operate.es.types.WorkflowType.ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.Map;

import org.camunda.operate.es.types.WorkflowType;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("elasticsearch")
public class WorkflowReader {

  @Autowired
  protected TransportClient esClient;

  public String getDiagram(String workflowId) {
    BoolQueryBuilder query = boolQuery().must(termQuery(ID, workflowId));

    SearchResponse response = esClient
        .prepareSearch(WorkflowType.TYPE)
        .setQuery(query)
        .setFetchSource(BPMN_XML, null)
        .setSize(1)
        .get();

    if (response.getHits().getTotalHits() > 0) {
      SearchHit hit = response.getHits().getAt(0);
      Map<String, Object> result = hit.getSourceAsMap();
      return (String) result.get(BPMN_XML);
    }
    else {
      throw new NotFoundException(String.format("Could not find xml for workflow with id '%s'.", workflowId));
    }
  }

}
