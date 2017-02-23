package org.camunda.optimize.service.importing;

/**
 * @author Askar Akhmerov
 */
public class ImportScheduleJob {
  private int totalPages;

  private ImportServiceProvider importServiceProvider;

  public int execute() {
    for (ImportService service : importServiceProvider.getServices()) {
      totalPages = totalPages + service.executeImport();
    }
    return totalPages;
  }

  public void setImportServiceProvider(ImportServiceProvider importServiceProvider) {
    this.importServiceProvider = importServiceProvider;
  }

}
