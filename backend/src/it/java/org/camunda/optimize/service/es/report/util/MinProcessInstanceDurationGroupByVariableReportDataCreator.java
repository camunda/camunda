package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationGroupByVariable;

public class MinProcessInstanceDurationGroupByVariableReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... additional) {
    if (additional.length == 0) {
      return createMinProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, "foo", "bar");
    } else if(additional.length == 1) {
      String variableName = additional[0];
      return createMinProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, "bar");
    } else {
      String variableName = additional[0];
      String variableType = additional[1];
      return createMinProcessInstanceDurationGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, variableType);
    }
  }
}
