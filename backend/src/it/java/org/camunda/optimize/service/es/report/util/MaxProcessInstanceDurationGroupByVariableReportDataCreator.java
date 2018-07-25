package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;

import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationGroupByVariable;

public class MaxProcessInstanceDurationGroupByVariableReportDataCreator implements ReportDataCreator {

  @Override
  public ReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... additional) {
    if (additional.length == 0) {
      return createMaxProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, "foo", "bar");
    } else if(additional.length == 1) {
      String variableName = additional[0];
      return createMaxProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, "bar");
    } else {
      String variableName = additional[0];
      String variableType = additional[1];
      return createMaxProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, variableType);
    }
  }
}
