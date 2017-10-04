package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.fetcher.EngineEntityFetcherImpl;
import org.camunda.optimize.service.importing.job.schedule.IdBasedImportScheduleJob;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public abstract class IdBasedImportService<ENG extends EngineDto, OPT extends OptimizeDto>
    extends AbstractImportService<ENG, OPT, IdBasedImportScheduleJob> {

  protected final String engineAlias;
  protected EngineEntityFetcherImpl engineEntityFetcher;

  public IdBasedImportService(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @Override
  public ImportResult executeImport(IdBasedImportScheduleJob job) throws OptimizeException {
    ImportResult result = new ImportResult();
    if (job.getIdsToFetch() != null && !job.getIdsToFetch().isEmpty()) {
      boolean engineHasStillNewData = false;
      logger.debug(
          "Importing based on [{}] IDs from type [{}]",
          job.getIdsToFetch().size(),
          getElasticsearchType()
      );

      List<ENG> pageOfEngineEntities = this.queryEngineRestPoint(job.getIdsToFetch(), job.getEngineAlias());
      List<ENG> newEngineEntities =
          getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
      if (!newEngineEntities.isEmpty()) {
        List<OPT> newOptimizeEntities = processNewEngineEntries(newEngineEntities, job.getEngineAlias());
        importToElasticSearch(newOptimizeEntities);
      }
      engineHasStillNewData = !pageOfEngineEntities.isEmpty();

      result.setElasticSearchType(this.getElasticsearchType());
      result.setEngineHasStillNewData(engineHasStillNewData);
      result.setIdsToFetch(getIdsForPostProcessing());
    }
    return result;
  }


  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint(Set<String> ids, String engineAlias) throws OptimizeException;

  public String getEngineName() {
    return this.engineAlias;
  }

  @Autowired
  public void setEngineEntityFetcher(EngineEntityFetcherImpl engineEntityFetcher) {
    this.engineEntityFetcher = engineEntityFetcher;
  }
}
