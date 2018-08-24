package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.types.WorkflowType;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.es.types.WorkflowType.BPMN_XML;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

@Component
public class WorkflowReader {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowType workflowType;

  /**
   * Gets the workflow diagram XML as a string.
   * @param workflowId
   * @return
   */
  public String getDiagram(String workflowId) {
    GetResponse response = esClient.prepareGet(workflowType.getType(), workflowType.getType(), workflowId).setFetchSource(BPMN_XML, null).get();

    if (response.isExists()) {
      Map<String, Object> result = response.getSourceAsMap();
      return (String) result.get(BPMN_XML);
    }
    else {
      throw new NotFoundException(String.format("Could not find xml for workflow with id '%s'.", workflowId));
    }
  }

  /**
   * Gets the workflow by id.
   * @param workflowId
   * @return
   */
  public WorkflowEntity getWorkflow(String workflowId) {
    final GetResponse response = esClient.prepareGet(workflowType.getType(), workflowType.getType(), workflowId).get();

    if (response.isExists()) {
      return fromSearchHit(response.getSourceAsString());
    }
    else {
      throw new NotFoundException(String.format("Could not find workflow with id '%s'.", workflowId));
    }
  }

  private WorkflowEntity fromSearchHit(String workflowString) {
    WorkflowEntity workflow;
    try {
      workflow = objectMapper.readValue(workflowString, WorkflowEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading workflow from Elasticsearch!", e);
      throw new RuntimeException("Error while reading workflow from Elasticsearch!", e);
    }
    return workflow;
  }

  public Map<String, List<WorkflowEntity>> getWorkflowsGrouped() {
    final String groupsAggName = "group_by_bpmnProcessId";
    final String workflowsAggName = "workflows";
    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowType.getType()).setSize(0)
        .addAggregation(
          terms(groupsAggName)
            .field(WorkflowType.BPMN_PROCESS_ID)
            .subAggregation(
              topHits(workflowsAggName)
                .fetchSource(new String[] { WorkflowType.ID, WorkflowType.NAME, WorkflowType.VERSION, WorkflowType.BPMN_PROCESS_ID  }, null)
                .size(100)
                .sort(WorkflowType.VERSION, SortOrder.DESC)));

    logger.debug("Grouped workflow request: \n{}", searchRequestBuilder.toString());

    final SearchResponse searchResponse = searchRequestBuilder.get();

    final Terms groups = searchResponse.getAggregations().get(groupsAggName);

    Map<String, List<WorkflowEntity>> result = new HashMap<>();

    groups.getBuckets().stream().forEach(b -> {
      final String bpmnProcessId = b.getKeyAsString();
      result.put(bpmnProcessId, new ArrayList<>());

      final TopHits workflows = b.getAggregations().get(workflowsAggName);
      final SearchHit[] hits = workflows.getHits().getHits();
      for (SearchHit searchHit: hits) {
        final WorkflowEntity workflowEntity = fromSearchHit(searchHit.getSourceAsString());
        result.get(bpmnProcessId).add(workflowEntity);
      }
    });

    return result;
  }
}
