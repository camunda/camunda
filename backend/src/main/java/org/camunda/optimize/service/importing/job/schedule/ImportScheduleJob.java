package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.ImportService;

/**
 * @author Askar Akhmerov
 */
public abstract class ImportScheduleJob <S extends ImportService> {

  protected S importService;
  protected boolean pageBased = true;

  public ImportResult execute() throws OptimizeException {
    return importService.executeImport();
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
}
