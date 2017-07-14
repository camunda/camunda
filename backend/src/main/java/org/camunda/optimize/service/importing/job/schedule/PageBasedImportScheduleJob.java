package org.camunda.optimize.service.importing.job.schedule;


import org.camunda.optimize.service.exceptions.BackoffException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;

import java.time.LocalDateTime;

/**
 * @author Askar Akhmerov
 */
public class PageBasedImportScheduleJob extends ImportScheduleJob<PaginatedImportService> {

  public ImportResult execute() throws OptimizeException {
    if (timeToExecute != null && LocalDateTime.now().isBefore(timeToExecute)) {
      throw new BackoffException();
    } else {
      ImportResult executionResult = importService.executeImport();
      return executionResult;
    }
  }
}
