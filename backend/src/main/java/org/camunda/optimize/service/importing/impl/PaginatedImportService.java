package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.util.ConfigurationReloadable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto>
  extends AbstractImportService <ENG, OPT> implements ConfigurationReloadable {

  protected ImportIndexHandler importIndexHandler;

  protected Set<String> idsForPostProcessing;

  @PostConstruct
  protected void init() {
    importIndexHandler = initializeImportIndexHandler();
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
    int searchedSize;
    importIndexHandler.makeSureIsInitialized();
    logger.debug("Importing page from type [{}] with index starting from [{}].",
        getElasticsearchType(), importIndexHandler.getRelativeImportIndex());

    List<ENG> pageOfEngineEntities = queryEngineRestPoint();
    searchedSize = pageOfEngineEntities.size();
    boolean engineHasStillNewData = searchedSize > 0;

    List<ENG> newEngineEntities =
        getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
    if (!newEngineEntities.isEmpty()) {
      List<OPT> newOptimizeEntities = processNewEngineEntries(newEngineEntities);
      importToElasticSearch(newOptimizeEntities);
    }

    if (!engineHasStillNewData) {
      engineHasStillNewData = importIndexHandler.adjustIndexWhenNoResultsFound(engineHasStillNewData);
    }

    importIndexHandler.moveImportIndex(searchedSize);
    importIndexHandler.persistImportIndexToElasticsearch();

    result.setEngineHasStillNewData(engineHasStillNewData);
    result.setIdsToFetch(getIdsForPostProcessing());
    this.idsForPostProcessing = null;
    return result;
  }



  public int getImportStartIndex() {
    return importIndexHandler.getAbsoluteImportIndex();
  }

  public void resetImportStartIndex() {
    importIndexHandler.resetImportIndex();
  }

  protected abstract ImportIndexHandler initializeImportIndexHandler();

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

  public void updateImportIndex() {
    this.importIndexHandler.updateImportIndex();
  }

  /**
   * Resets the process definitions to import, but keeps the relative indexes
   * from every respective process definition. Thus, we are not importing
   * all the once again, but starting from the last point we stopped at.
   */
  public void restartImportCycle() {
    importIndexHandler.restartImportCycle();
  }

  public ImportIndexHandler getImportIndexHandler() {
    return importIndexHandler;
  }
}
