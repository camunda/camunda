package org.camunda.optimize.service.importing.job.schedule;

import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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

  @Autowired
  protected IndexHandlerProvider indexHandlerProvider;

  public Collection<? extends ImportScheduleJob> createPagedJobs() {
    List<ImportScheduleJob> result = null;
    for (String engine : configurationService.getConfiguredEngines().keySet()) {
      result = createPagedJobs(engine);
    }
    return result;
  }

  public List<ImportScheduleJob> createPagedJobs(String engine) {
    List<ImportScheduleJob> result;
    result = new ArrayList<>();
    if (importServiceProvider.getPagedServices(engine) != null) {
      for (PaginatedImportService service : importServiceProvider.getPagedServices(engine)) {
        ImportIndexHandler importIndexHandler =
            indexHandlerProvider.getIndexHandler(service.getElasticsearchType(), service.getIndexHandlerType(), engine);

        importIndexHandler.makeSureIsInitialized();

        PageBasedImportScheduleJob job = constructJob(service, importIndexHandler, engine);
        result.add(job);
      }
    }
    return result;
  }

  private PageBasedImportScheduleJob constructJob(PaginatedImportService service, ImportIndexHandler importIndexHandler, String engine) {
    PageBasedImportScheduleJob job;
    if(service.isProcessDefinitionBased()) {
      DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler = (DefinitionBasedImportIndexHandler) importIndexHandler;
      job = new PageBasedImportScheduleJob(
          importIndexHandler.getAbsoluteImportIndex(),
          importIndexHandler.getRelativeImportIndex(),
          definitionBasedImportIndexHandler.getCurrentDefinitionBasedImportIndex(),
          definitionBasedImportIndexHandler.getCurrentProcessDefinitionId()
      );
    } else {
      job = new PageBasedImportScheduleJob(
          importIndexHandler.getAbsoluteImportIndex(),
          importIndexHandler.getRelativeImportIndex()
      );
    }
    job.setEngineAlias(engine);
    job.setElasticsearchType(service.getElasticsearchType());
    return job;
  }

  public List<ImportScheduleJob> createIndexedScheduleJobs(Set<String> idsToFetch, String engineAlias) {
    List<ImportScheduleJob> jobs = new ArrayList<>();
    if (idsToFetch != null) {
      addHistoricProcessInstanceScheduleJobs(jobs, idsToFetch, engineAlias);
    }
    return jobs;
  }

  private void addHistoricProcessInstanceScheduleJobs(List<ImportScheduleJob> jobs, Set<String> idsToFetch, String engineAlias) {
    List<Set<String>> batches =
      splitUpSetIntoListOfBatches(idsToFetch, configurationService.getEngineImportProcessInstanceMaxPageSize());
    for (Set<String> batch : batches) {
      jobs.add(createHistoricProcessInstanceScheduleJob(batch, engineAlias));
    }
  }

  private List<Set<String>> splitUpSetIntoListOfBatches(Set<String> ids, int batchSize) {
    List<Set<String>> batches = new ArrayList<>();
    Set<String> batch = new HashSet<>();
    for (String id : ids) {
      batch.add(id);
      if( batch.size() >= batchSize) {
        batches.add(batch);
        batch = new HashSet<>();
      }
    }
    batches.add(batch);
    return batches;
  }

  private ImportScheduleJob createHistoricProcessInstanceScheduleJob(Set<String> idsToFetch, String engineAlias) {
    IdBasedImportScheduleJob job = new IdBasedImportScheduleJob();
    job.setElasticsearchType(importServiceProvider.getProcessInstanceImportService(engineAlias).getElasticsearchType());
    job.setIdsToFetch(idsToFetch);
    job.setPageBased(false);
    job.setEngineAlias(engineAlias);
    return job;
  }

  private ImportScheduleJob createHistoricVariableInstanceScheduleJob(Set<String> idsToFetch, String engineAlias) {
    IdBasedImportScheduleJob job = new IdBasedImportScheduleJob();
    job.setElasticsearchType(importServiceProvider.getVariableImportService(engineAlias).getElasticsearchType());
    job.setIdsToFetch(idsToFetch);
    job.setPageBased(false);
    job.setEngineAlias(engineAlias);
    return job;
  }

  public ImportScheduleJob createPagedJob(String elasticsearchType, String engine) {
    PaginatedImportService importService = importServiceProvider.getPaginatedImportService(elasticsearchType, engine);
    ImportIndexHandler indexHandler = indexHandlerProvider.getIndexHandler(elasticsearchType, importService.getIndexHandlerType(), engine);
    return constructJob(importService, indexHandler, engine);
  }
}
