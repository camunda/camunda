package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.fetcher.DefinitionBasedEngineEntityFetcher;
import org.camunda.optimize.service.importing.strategy.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.util.ConfigurationReloadable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto>
  extends AbstractImportService <ENG, OPT> implements ConfigurationReloadable {

  @Autowired
  protected DefinitionBasedImportIndexHandler importStrategy;

  @Autowired
  protected DefinitionBasedEngineEntityFetcher engineEntityFetcher;

  protected Set<String> idsForPostProcessing;

  @PostConstruct
  protected void init() {
    importStrategy.initializeImportIndex(getElasticsearchType(), getEngineImportMaxPageSize());
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    init();
  }

  @Override
  protected List<OPT> processNewEngineEntries(List<ENG> entries) {
    this.idsForPostProcessing = new HashSet<>();
    return super.processNewEngineEntries(entries);
  }

  @Override
  public ImportResult executeImport() throws OptimizeException {
    ImportResult result = new ImportResult();
    boolean engineHasStillNewData = false;
    int searchedSize;
    importStrategy.makeSureIsInitialized();
    logger.debug("Importing page from type [{}] with index starting from [{}].",
        getElasticsearchType(), importStrategy.getRelativeImportIndex());

    List<ENG> pageOfEngineEntities = queryEngineRestPoint();
    searchedSize = pageOfEngineEntities.size();
    engineHasStillNewData = searchedSize > 0;

    List<ENG> newEngineEntities =
        getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
    if (!newEngineEntities.isEmpty()) {
      List<OPT> newOptimizeEntities = processNewEngineEntries(newEngineEntities);
      importToElasticSearch(newOptimizeEntities);
    } else {
      engineHasStillNewData = importStrategy.adjustIndexWhenNoResultsFound(engineHasStillNewData);
    }

    importStrategy.moveImportIndex(searchedSize);
    importStrategy.persistImportIndexToElasticsearch();

    result.setEngineHasStillNewData(engineHasStillNewData);
    result.setIdsToFetch(getIdsForPostProcessing());
    this.idsForPostProcessing = null;
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

  public void updateDefinitionsToImport() {
    this.importStrategy.updateDefinitionsToImportFromEngine();
  }

  /**
   * Resets the process definitions to import, but keeps the relative indexes
   * from every respective process definition. Thus, we are not importing
   * all the once again, but starting from the last point we stopped at.
   */
  public void restartImportCycle() {
    importStrategy.restartDefinitionBasedImportCycle();
  }
}
