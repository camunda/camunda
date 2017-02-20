package org.camunda.optimize.service.importing;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Askar Akhmerov
 */
public class ImportScheduleJob extends Thread {
  private int totalPages;

  @Autowired
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
