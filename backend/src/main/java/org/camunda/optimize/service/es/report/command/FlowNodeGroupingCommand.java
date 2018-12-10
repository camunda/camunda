package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.FLOW_NODE_NAMES;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_XML;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;


public abstract class FlowNodeGroupingCommand extends ProcessReportCommand<MapProcessReportResultDto> {

  @Override
  protected MapProcessReportResultDto filterResultData(MapProcessReportResultDto evaluationResult) {
    MapProcessReportResultDto resultDto = evaluationResult;
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(getProcessReportData().getProcessDefinitionVersion())) {
      ProcessDefinitionOptimizeDto latestXml = fetchLatestDefinitionXml();
      Map<String, Long> filteredNodes = new HashMap<>();

      for (Map.Entry<String, Long> node : resultDto.getResult().entrySet()) {
        if (latestXml.getFlowNodeNames().containsKey(node.getKey())) {
          filteredNodes.put(node.getKey(), node.getValue());
        }
      }

      resultDto.setResult(filteredNodes);
    }
    return resultDto;
  }

  protected ProcessDefinitionOptimizeDto fetchLatestDefinitionXml() {
    ProcessDefinitionOptimizeDto result = null;
    BoolQueryBuilder query = boolQuery()
      .must(termQuery("processDefinitionKey", getProcessReportData().getProcessDefinitionKey()));

    SearchResponse scrollResp = esclient
      .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .addSort(ProcessInstanceType.PROCESS_DEFINITION_VERSION, SortOrder.DESC)
      .setSize(1)
      .get();

    if (scrollResp.getHits().getTotalHits() > 0) {
      try {
        ProcessInstanceDto processInstanceDto =
          objectMapper.readValue(scrollResp.getHits().getAt(0).getSourceAsString(), ProcessInstanceDto.class);
        String processDefinitionId = processInstanceDto.getProcessDefinitionId();

        GetResponse response = esclient.prepareGet(
          getOptimizeIndexAliasForType(configurationService.getProcessDefinitionType()),
          configurationService.getProcessDefinitionType(),
          processDefinitionId
        )
          .get();

        result = new ProcessDefinitionOptimizeDto();
        result.setBpmn20Xml(response.getSource().get(PROCESS_DEFINITION_XML).toString());
        result.setId(response.getSource().get(PROCESS_DEFINITION_ID).toString());
        if (response.getSource().get(ENGINE) != null) {
          result.setEngine(response.getSource().get(ENGINE).toString());
        }
        result.setFlowNodeNames((Map<String, String>) response.getSource().get(FLOW_NODE_NAMES));

      } catch (IOException e) {
        logger.error("can't parse process instance", e);
      }
    }
    return result;
  }
}
