package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;


public abstract class FlowNodeGroupingCommand extends ProcessReportCommand<ProcessReportMapResultDto> {

  @Override
  protected ProcessReportMapResultDto filterResultData(ProcessReportMapResultDto evaluationResult) {
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(getProcessReportData().getProcessDefinitionVersion())) {
      ProcessDefinitionOptimizeDto latestXml = fetchLatestDefinitionXml();
      Map<String, Long> filteredNodes = new HashMap<>();

      for (Map.Entry<String, Long> node : evaluationResult.getResult().entrySet()) {
        if (latestXml.getFlowNodeNames().containsKey(node.getKey())) {
          filteredNodes.put(node.getKey(), node.getValue());
        }
      }

      evaluationResult.setResult(filteredNodes);
    }
    return evaluationResult;
  }

  private ProcessDefinitionOptimizeDto fetchLatestDefinitionXml() {
    BoolQueryBuilder query = boolQuery()
      .must(termQuery(ProcessDefinitionType.PROCESS_DEFINITION_KEY, getProcessReportData().getProcessDefinitionKey()));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(ProcessDefinitionType.PROCESS_DEFINITION_VERSION, SortOrder.DESC)
      .size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_DEF_TYPE))
        .types(PROC_DEF_TYPE)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits() > 0) {
        String responseBodyAsString = response.getHits().getAt(0).getSourceAsString();
        return objectMapper.readValue(responseBodyAsString, ProcessDefinitionOptimizeDto.class);
      } else {
        String reason = "Could not fetch process instance for latest process definition. " +
          "It seems the process definition hast not been imported yet.";
        logger.error(reason);
        throw new OptimizeRuntimeException(reason);
      }
    } catch (IOException e) {
      String reason = "Could not fetch process instance for latest process definition.";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }
}
