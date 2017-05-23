package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.ImportStrategy;
import org.camunda.optimize.service.importing.ImportStrategyProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto> extends AbstractImportService<ENG, OPT> {

  protected ImportStrategy importStrategy;

  @Autowired
  protected ImportStrategyProvider importStrategyProvider;

  protected Set<String> idsForPostProcessing;

  @PostConstruct
  protected void init() {
    importStrategy = importStrategyProvider.getImportStrategyInstance();
    importStrategy.initializeImportIndex(getElasticsearchType(), getEngineImportMaxPageSize());
  }

  @Override
  protected List<OPT> processNewEngineEntries(List<ENG> entries) {
    this.idsForPostProcessing = new HashSet<>();
    return super.processNewEngineEntries(entries);
  }

  @Override
  public ImportResult executeImport() throws OptimizeException {
    ImportResult result = new ImportResult();
    int pagesWithData = 0;
    int searchedSize;
    logger.debug("Importing page from type [{}] with index starting from [{}].",
        getElasticsearchType(), importStrategy.getRelativeImportIndex());

    List<ENG> pageOfEngineEntities = queryEngineRestPoint();
    List<ENG> newEngineEntities =
        getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
    if (!newEngineEntities.isEmpty()) {
      pagesWithData = pagesWithData + 1;
      List<OPT> newOptimizeEntities = processNewEngineEntries(newEngineEntities);
      importToElasticSearch(newOptimizeEntities);
    } else {
      pagesWithData = importStrategy.adjustIndexWhenNoResultsFound(pagesWithData);
    }
    searchedSize = pageOfEngineEntities.size();

    if (pagesWithData != 0) {
      importStrategy.moveImportIndex(searchedSize);
      importStrategy.persistImportIndexToElasticsearch();
    }

    result.setPagesPassed(pagesWithData);
    result.setIdsToFetch(getIdsForPostProcessing());
    return result;
  }

  protected int getEngineImportMaxPageSize() {
    return configurationService.getEngineImportMaxPageSize();
  }

  public int getImportStartIndex() {
    return importStrategy.getAbsoluteImportIndex();
  }

  public void resetImportStartIndex() {
    importStrategy.resetImportIndex();
  }

  public void updateImportIndex() {
    importStrategy.updateConfigurationSettings();
  }

  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint() throws OptimizeException;

  /**
   * @return Return the total number of entities that are to be expected to
   * be imported from the engine.
   */
  public abstract int getEngineEntityCount() throws OptimizeException;

}
