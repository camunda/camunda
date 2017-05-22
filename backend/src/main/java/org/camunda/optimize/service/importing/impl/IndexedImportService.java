package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.camunda.optimize.service.importing.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public abstract class IndexedImportService <ENG extends EngineDto, OPT extends OptimizeDto> extends AbstractImportService <ENG, OPT> {

  @Autowired
  protected EngineEntityFetcher engineEntityFetcher;

  private Set<String> idsForImport;

  public Set<String> getIdsForImport() {
    return idsForImport;
  }

  public void setIdsForImport(Set<String> idsForImport) {
    this.idsForImport = idsForImport;
  }

  public ImportResult executeImport() throws OptimizeException {
    ImportResult result = new ImportResult();
    if (this.getIdsForImport() != null && !getIdsForImport().isEmpty()) {
      int pagesWithData = 0;
      logger.debug(
          "Importing based on [{}] IDs from type [{}]",
          this.getIdsForImport().size(),
          getElasticsearchType()
      );

      List<ENG> pageOfEngineEntities = this.queryEngineRestPoint(this.getIdsForImport());
      List<ENG> newEngineEntities =
          getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
      if (!newEngineEntities.isEmpty()) {
        pagesWithData = pagesWithData + 1;
        List<OPT> newOptimizeEntities = mapToOptimizeDto(newEngineEntities);
        importToElasticSearch(newOptimizeEntities);
      }

      result.setPagesPassed(pagesWithData);
      result.setIdsToFetch(getIdsForPostProcessing());
    }
    return result;
  }


  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint(Set<String> ids) throws OptimizeException;

}
