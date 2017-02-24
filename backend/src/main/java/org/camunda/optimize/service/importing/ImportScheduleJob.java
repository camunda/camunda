package org.camunda.optimize.service.importing;

/**
 * @author Askar Akhmerov
 */
public class ImportScheduleJob {

  private ImportService importService;

  public int execute() {
    return importService.executeImport();
  }

  public void setImportService(ImportService importService) {
    this.importService = importService;
  }

  public ImportService getImportService() {
    return importService;
  }
}
