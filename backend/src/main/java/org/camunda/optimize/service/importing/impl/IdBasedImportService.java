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

  @Autowired
  protected EngineEntityFetcherImpl engineEntityFetcher;

  protected Set<String> idsForImport;

  public Set<String> getIdsForImport() {
    return idsForImport;
  }

  public void setIdsForImport(Set<String> idsForImport) {
    this.idsForImport = idsForImport;
  }

  @Override
  public ImportResult executeImport(IdBasedImportScheduleJob job) throws OptimizeException {
    ImportResult result = new ImportResult();
    if (this.getIdsForImport() != null && !getIdsForImport().isEmpty()) {
      boolean engineHasStillNewData = false;
      logger.debug(
          "Importing based on [{}] IDs from type [{}]",
          this.getIdsForImport().size(),
          getElasticsearchType()
      );

      List<ENG> pageOfEngineEntities = this.queryEngineRestPoint(this.getIdsForImport(), job.getEngineAlias());
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

}
