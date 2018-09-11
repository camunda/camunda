package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_HOUR;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_MONTH;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_WEEK;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_YEAR;

public class ReportUtil {

  private static Logger logger = LoggerFactory.getLogger(ReportUtil.class);

  public static void copyReportData(SingleReportDataDto from, SingleReportResultDto to) {
    SingleReportDataDto reportDataDto = new SingleReportDataDto();
    reportDataDto.setProcessDefinitionKey(from.getProcessDefinitionKey());
    reportDataDto.setProcessDefinitionVersion(from.getProcessDefinitionVersion());
    reportDataDto.setView(from.getView());
    reportDataDto.setGroupBy(from.getGroupBy());
    reportDataDto.setFilter(from.getFilter());
    reportDataDto.setProcessPart(from.getProcessPart());
    reportDataDto.setVisualization(from.getVisualization());
    reportDataDto.setConfiguration(from.getConfiguration());
    to.setData(reportDataDto);
  }

  public static DateHistogramInterval getDateHistogramInterval(String interval) throws OptimizeException {
    switch (interval) {
      case DATE_UNIT_YEAR:
        return DateHistogramInterval.YEAR;
      case DATE_UNIT_MONTH:
        return DateHistogramInterval.MONTH;
      case DATE_UNIT_WEEK:
        return DateHistogramInterval.WEEK;
      case DATE_UNIT_DAY:
        return DateHistogramInterval.DAY;
      case DATE_UNIT_HOUR:
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
}
