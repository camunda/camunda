package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public abstract class FlowNodeGroupingCommand extends ReportCommand<MapReportResultDto> {

  @Override
  protected MapReportResultDto filterResultDataBasedOnPD(MapReportResultDto evaluationResult) {
    MapReportResultDto resultDto = evaluationResult;
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      ProcessDefinitionXmlOptimizeDto latestXml = super.fetchLatestDefinitionXml();
      Map<String, Long> filteredNodes = new HashMap<>();

      for (Map.Entry<String, Long> node : resultDto.getResult().entrySet()) {
        if (latestXml.getFlowNodeNames().containsValue(node.getKey())) {
          filteredNodes.put(node.getKey(), node.getValue());
        }
      }

      resultDto.setResult(filteredNodes);
    }
    return resultDto;
  }
}
