package org.camunda.optimize.service.importing;

/**
 * @author Askar Akhmerov
 */
public class ImportScheduleJob extends Thread {
  private int totalPages;

  private ImportServiceProvider importServiceProvider;

  @Override
  public void run() {
    for (ImportService service : importServiceProvider.getServices()) {
      totalPages = totalPages + service.executeImport();
    }
  }

  public ImportServiceProvider getImportServiceProvider() {
    return importServiceProvider;
  }

  public void setImportServiceProvider(ImportServiceProvider importServiceProvider) {
    this.importServiceProvider = importServiceProvider;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public void setTotalPages(int totalPages) {
    this.totalPages = totalPages;
  }
}
