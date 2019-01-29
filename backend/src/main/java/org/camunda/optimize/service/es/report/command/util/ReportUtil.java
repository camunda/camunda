package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportUtil {

  private static final String STATS_AGGREGATION = "minMaxValueOfData";
  private static final Logger logger = LoggerFactory.getLogger(ReportUtil.class);
  public static final int NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION = 80;

  public static void copyReportData(ReportDataDto from, ReportResultDto to) {
    if (to instanceof ProcessReportResultDto) {
      final ProcessReportResultDto processReportResultDto = (ProcessReportResultDto) to;
      processReportResultDto.setData((ProcessReportDataDto) from);
    } else if (to instanceof DecisionReportResultDto) {
      final DecisionReportResultDto decisionReportResultDto = (DecisionReportResultDto) to;
      decisionReportResultDto.setData((DecisionReportDataDto) from);
    } else {
      throw new IllegalStateException("Unsupported result dto: " + to.getClass().getSimpleName());
    }
  }

  public static void copyCombinedReportMetaData(CombinedReportDefinitionDto from, CombinedReportDefinitionDto to) {
    copyMetaData(from, to);
    to.setData(from.getData());
  }

  public static void copyMetaData(ReportDefinitionDto from, ReportDefinitionDto to) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setOwner(from.getOwner());
    to.setCreated(from.getCreated());
    to.setLastModifier(from.getLastModifier());
    to.setLastModified(from.getLastModified());
  }

  public static void copyDefinitionMetaDataToUpdate(ReportDefinitionDto from, ReportDefinitionUpdateDto to) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setOwner(from.getOwner());
    to.setLastModifier(from.getLastModifier());
    to.setLastModified(from.getLastModified());
  }

  public static <O extends Combinable> boolean isCombinable(O o1, O o2) {
    if (o1 == null && o2 == null) {
      return true;
    } else if (o1 == null) {
      return false;
    } else if (o2 == null) {
      return false;
    } else {
      return o1.isCombinable(o2);
    }
  }
}
