package org.camunda.optimize.service.importing.job.schedule;


import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;

/**
 * @author Askar Akhmerov
 */
public class PageBasedImportScheduleJob extends ImportScheduleJob <PaginatedImportService> {

  private Integer indexBeforeExecution;
  private Integer indexAfterExecution;

  public ImportResult execute() throws OptimizeException {
    this.indexBeforeExecution = importService.getImportStartIndex();
    ImportResult executionResult= importService.executeImport();
    this.indexAfterExecution = importService.getImportStartIndex();
    return executionResult;
  }

  public Integer getIndexBeforeExecution() {
    return indexBeforeExecution;
  }

  public Integer getIndexAfterExecution() {
    return indexAfterExecution;
  }
}
