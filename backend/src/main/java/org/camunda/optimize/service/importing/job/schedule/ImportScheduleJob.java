package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.exceptions.BackoffException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.ImportService;

import java.time.LocalDateTime;

/**
 * @author Askar Akhmerov
 */
public abstract class ImportScheduleJob<S extends ImportService> {

  protected S importService;
  protected boolean pageBased = true;
  protected LocalDateTime timeToExecute;

  public ImportResult execute() throws OptimizeException {
    ImportResult result;
    if (timeToExecute != null && LocalDateTime.now().isBefore(timeToExecute)) {
      throw new BackoffException();
    } else {
      result =  importService.executeImport(this);
    }
    return result;
  }

  public void setImportService(S importService) {
    this.importService = importService;
  }

  public S getImportService() {
    return importService;
  }

  public boolean isPageBased() {
    return pageBased;
  }

  public void setPageBased(boolean pageBased) {
    this.pageBased = pageBased;
  }

  public LocalDateTime getTimeToExecute() {
    return timeToExecute;
  }

  public void setTimeToExecute(LocalDateTime timeToExecute) {
    this.timeToExecute = timeToExecute;
  }
}
