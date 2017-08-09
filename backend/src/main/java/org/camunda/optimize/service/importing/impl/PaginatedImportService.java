package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto, IH extends ImportIndexHandler>
  extends AbstractImportService <ENG, OPT, PageBasedImportScheduleJob> {

  protected Set<String> idsForPostProcessing;

  public abstract Class<IH> getIndexHandlerType();

  @Override
  protected List<OPT> processNewEngineEntries(List<ENG> entries) {
    this.idsForPostProcessing = new HashSet<>();
    return super.processNewEngineEntries(entries);
  }

  @Override
  public ImportResult executeImport(PageBasedImportScheduleJob job) throws OptimizeException {
    ImportResult result = new ImportResult();
    int searchedSize;

    logger.debug("Importing page from type [{}] with index starting from [{}].",
        getElasticsearchType(), job.getRelativeImportIndex());

    List<ENG> pageOfEngineEntities = queryEngineRestPoint(job);
    searchedSize = pageOfEngineEntities.size();
    boolean engineHasStillNewData = searchedSize > 0;

    List<ENG> newEngineEntities =
        getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
    if (!newEngineEntities.isEmpty()) {
      List<OPT> newOptimizeEntities = processNewEngineEntries(newEngineEntities);
      importToElasticSearch(newOptimizeEntities);
    }

    result.setElasticSearchType(this.getElasticsearchType());
    result.setIndexHandlerType(this.getIndexHandlerType());
    result.setSearchedSize(searchedSize);
    result.setEngineHasStillNewData(engineHasStillNewData);
    result.setIdsToFetch(getIdsForPostProcessing());
    this.idsForPostProcessing = null;
    return result;
  }

  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint(PageBasedImportScheduleJob job) throws OptimizeException;

  /**
   * @return Return the total number of entities that are to be expected to
   * be imported from the engine.
   */
  public abstract int getEngineEntityCount(IH indexHandler) throws OptimizeException;

  public boolean isProcessDefinitionBased() {
    return false;
  }
}
