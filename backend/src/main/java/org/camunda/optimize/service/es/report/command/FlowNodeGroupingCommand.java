package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;

import java.util.HashMap;
import java.util.Map;


public abstract class FlowNodeGroupingCommand extends ReportCommand<MapProcessReportResultDto> {

  @Override
  protected MapProcessReportResultDto filterResultDataBasedOnPD(MapProcessReportResultDto evaluationResult) {
    MapProcessReportResultDto resultDto = evaluationResult;
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      ProcessDefinitionOptimizeDto latestXml = super.fetchLatestDefinitionXml();
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
}
