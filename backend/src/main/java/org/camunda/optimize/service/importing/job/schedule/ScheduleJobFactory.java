package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.importing.ImportServiceProvider;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
@Component
public class ScheduleJobFactory {
  @Autowired
  protected ImportServiceProvider importServiceProvider;

  public Collection<? extends ImportScheduleJob> createPagedJobs() {
    List<ImportScheduleJob> result = new ArrayList<>();
    for (PaginatedImportService service : importServiceProvider.getPagedServices()) {
      PageBasedImportScheduleJob job = new PageBasedImportScheduleJob();
      job.setImportService(service);
      result.add(job);
    }
    return result;
  }

  public ImportScheduleJob createHistoricProcessInstanceScheduleJob(Set<String> idsToFetch) {
    IdBasedImportScheduleJob hpiJob = new IdBasedImportScheduleJob();
    hpiJob.setImportService(importServiceProvider.getProcessInstanceImportService());
    hpiJob.setIdsToFetch(idsToFetch);
    return hpiJob;
  }

}
