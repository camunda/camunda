package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.importing.ImportServiceProvider;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
@Component
public class ScheduleJobFactory {
  @Autowired
  protected ImportServiceProvider importServiceProvider;

  @Autowired
  protected ConfigurationService configurationService;

  public Collection<? extends ImportScheduleJob> createPagedJobs() {
    List<ImportScheduleJob> result = new ArrayList<>();
    for (PaginatedImportService service : importServiceProvider.getPagedServices()) {
      PageBasedImportScheduleJob job = new PageBasedImportScheduleJob();
      job.setImportService(service);
      result.add(job);
    }
    return result;
  }

  public List<ImportScheduleJob> createIndexedScheduleJobs(Set<String> idsToFetch) {
    List<ImportScheduleJob> jobs = new ArrayList<>();
    if (idsToFetch != null) {
      jobs.add(createHistoricProcessInstanceScheduleJob(idsToFetch));
      Set <String> hviPage = new HashSet<>();
      for (String id : idsToFetch) {
        hviPage.add(id);
        if (hviPage.size() == configurationService.getMaxVariablesPageSize()) {
          jobs.add(createHistoricVariableInstanceScheduleJob(hviPage));
          hviPage = new HashSet<>();
        }
      }
      jobs.add(createHistoricVariableInstanceScheduleJob(hviPage));
    }
    return jobs;
  }

  private ImportScheduleJob createHistoricProcessInstanceScheduleJob(Set<String> idsToFetch) {
    IdBasedImportScheduleJob job = new IdBasedImportScheduleJob();
    job.setImportService(importServiceProvider.getProcessInstanceImportService());
    job.setIdsToFetch(idsToFetch);
    job.setPageBased(false);
    return job;
  }

  private ImportScheduleJob createHistoricVariableInstanceScheduleJob(Set<String> idsToFetch) {
    IdBasedImportScheduleJob job = new IdBasedImportScheduleJob();
    job.setImportService(importServiceProvider.getVariableImportService());
    job.setIdsToFetch(idsToFetch);
    job.setPageBased(false);
    return job;
  }

}
