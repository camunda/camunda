package org.camunda.optimize.service.es.report.util.creator.avg;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;

import static org.camunda.optimize.test.util.ReportDataHelper.createAverageProcessInstanceDurationGroupByVariable;

public class AvgProcessInstanceDurationGroupByVariableReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... additional) {
    if (additional.length == 0) {
      return createAverageProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, "foo", "bar");
    } else if(additional.length == 1) {
      String variableName = additional[0];
      return createAverageProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, "bar");
    } else {
      String variableName = additional[0];
      String variableType = additional[1];
      return createAverageProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, variableType);
    }
  }
}
