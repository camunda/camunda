package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

public class IdleImportScheduleJob extends ImportScheduleJob<ImportService> {

  private Logger logger = LoggerFactory.getLogger(IdleImportScheduleJob.class);

  @Override
  public ImportResult execute() throws OptimizeException {
    // be idle and do nothing
    logger.warn("Scheduled an idle import schedule job, which should not happen in normal cases!");
    ImportResult result = new ImportResult();
    result.setEngineHasStillNewData(false);
    result.setIdsToFetch(new HashSet<>());
    return new ImportResult();
  }

}
