package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_HOUR;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_MONTH;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_WEEK;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_YEAR;

public class ReportDataUtil {

  private static Logger logger = LoggerFactory.getLogger(ReportDataUtil.class);

  public static void copyReportData(ReportDataDto from, ReportDataDto to) {
    to.setProcessDefinitionId(from.getProcessDefinitionId());
    to.setView(from.getView());
    to.setGroupBy(from.getGroupBy());
    to.setFilter(from.getFilter());
    to.setVisualization(from.getVisualization());
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


}
