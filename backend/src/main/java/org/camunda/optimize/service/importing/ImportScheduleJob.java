package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;

/**
 * @author Askar Akhmerov
 */
public class ImportScheduleJob {

  private ImportService importService;

  public int execute() throws OptimizeException {
    return importService.executeImport();
  }

  public void setImportService(ImportService importService) {
    this.importService = importService;
  }

  public ImportService getImportService() {
    return importService;
  }
}
