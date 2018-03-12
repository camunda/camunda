package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NotSupportedCommand extends ReportCommand <ReportResultDto> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  protected ReportResultDto evaluate() throws OptimizeException {
    // Error should contain the report Name
    try {
      logger.warn("The following settings combination of the report data is not supported in Optimize: \n" +
        "{} \n " +
        "Therefore returning error result.", objectMapper.writeValueAsString(reportData));
    } catch (JsonProcessingException e) {
      logger.error("can't serialize report data", e);
    }
    throw new OptimizeException("This combination of the settings of the report builder is not supported!");
  }
}
