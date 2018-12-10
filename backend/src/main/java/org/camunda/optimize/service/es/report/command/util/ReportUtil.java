package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportUtil {

  private static Logger logger = LoggerFactory.getLogger(ReportUtil.class);

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

  public static DateHistogramInterval getDateHistogramInterval(GroupByDateUnit interval) throws OptimizeException {
    switch (interval) {
      case YEAR:
        return DateHistogramInterval.YEAR;
      case MONTH:
        return DateHistogramInterval.MONTH;
      case WEEK:
        return DateHistogramInterval.WEEK;
      case DAY:
        return DateHistogramInterval.DAY;
      case HOUR:
        return DateHistogramInterval.HOUR;
      default:
        logger.error("Unknown interval {}. Please state a valid interval", interval);
        throw new OptimizeException("Unknown interval used. Please state a valid interval.");
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
