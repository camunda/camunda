package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;

public class ReportDataUtil {

  public static void copyReportData(ReportDataDto from, ReportDataDto to) {
    to.setProcessDefinitionId(from.getProcessDefinitionId());
    to.setView(from.getView());
    to.setGroupBy(from.getGroupBy());
    to.setFilter(from.getFilter());
    to.setVisualization(from.getVisualization());
  }
}
