package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NotSupportedCommand extends ReportCommand {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  protected ReportResultDto evaluate() throws IOException, OptimizeException {
    // Error should contain the report Name
    logger.warn("The following settings combination of the report data is not supported in Optimize: \n" +
      "{} \n " +
      "Therefore returning error result.", objectMapper.writeValueAsString(reportData));
    throw new OptimizeException("This combination of the settings of the report builder is not supported!");
  }
}
