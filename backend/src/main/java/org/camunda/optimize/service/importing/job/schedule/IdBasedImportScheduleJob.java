package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.impl.IdBasedImportService;

import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class IdBasedImportScheduleJob extends ImportScheduleJob {
  private Set<String> idsToFetch;

  public Set<String> getIdsToFetch() {
    return idsToFetch;
  }

  public void setIdsToFetch(Set<String> idsToFetch) {
    this.idsToFetch = idsToFetch;
  }

}
