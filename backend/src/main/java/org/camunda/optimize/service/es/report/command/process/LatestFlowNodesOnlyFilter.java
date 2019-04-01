package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

class LatestFlowNodesOnlyFilter {
  private static final Logger logger = LoggerFactory.getLogger(LatestFlowNodesOnlyFilter.class);

  private LatestFlowNodesOnlyFilter() {
  }

  static SingleProcessMapReportResult filterResultData(final CommandContext<SingleProcessReportDefinitionDto> commandContext,
                                                       final SingleProcessMapReportResult evaluationResult) {
    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      final ProcessDefinitionOptimizeDto latestXml = fetchLatestDefinitionXml(commandContext);
      final Map<String, Long> filteredNodes = new HashMap<>();

      for (Map.Entry<String, Long> node : evaluationResult.getResultAsDto().getData().entrySet()) {
        if (latestXml.getFlowNodeNames().containsKey(node.getKey())) {
          filteredNodes.put(node.getKey(), node.getValue());
        }
      }

      evaluationResult.getResultAsDto().setData(filteredNodes);
    }
    return evaluationResult;
  }

  static SingleProcessMapDurationReportResult filterResultData(final CommandContext<SingleProcessReportDefinitionDto> commandContext,
                                                               final SingleProcessMapDurationReportResult evaluationResult) {
    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      final ProcessDefinitionOptimizeDto latestXml = fetchLatestDefinitionXml(commandContext);
      final Map<String, AggregationResultDto> filteredNodes = new HashMap<>();

      for (Map.Entry<String, AggregationResultDto> node : evaluationResult.getResultAsDto().getData().entrySet()) {
        if (latestXml.getFlowNodeNames().containsKey(node.getKey())) {
          filteredNodes.put(node.getKey(), node.getValue());
        }
      }

      evaluationResult.getResultAsDto().setData(filteredNodes);
    }
    return evaluationResult;
  }

  private static ProcessDefinitionOptimizeDto fetchLatestDefinitionXml(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final BoolQueryBuilder query = boolQuery()
      .must(termQuery(
        ProcessDefinitionType.PROCESS_DEFINITION_KEY,
        commandContext.getReportDefinition().getData().getProcessDefinitionKey()
      ));

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(ProcessDefinitionType.PROCESS_DEFINITION_VERSION, SortOrder.DESC)
      .size(1);
    final SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_DEF_TYPE))
        .types(PROC_DEF_TYPE)
        .source(searchSourceBuilder);

    final SearchResponse response;
    try {
      response = commandContext.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits() > 0) {
        final String responseBodyAsString = response.getHits().getAt(0).getSourceAsString();
        return commandContext.getObjectMapper().readValue(responseBodyAsString, ProcessDefinitionOptimizeDto.class);
      } else {
        final String reason = "Could not fetch latest process definition." +
          "It seems the process definition has not been imported yet.";
        logger.error(reason);
        throw new OptimizeRuntimeException(reason);
      }
    } catch (IOException e) {
      final String reason = "Could not fetch latest process definition.";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

}
