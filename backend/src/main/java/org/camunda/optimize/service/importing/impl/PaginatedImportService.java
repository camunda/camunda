package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.ensureGreaterThanZero;

@Component
abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto> implements ImportService {

  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected EngineEntityFetcher engineEntityFetcher;
  @Autowired
  protected ImportJobExecutor importJobExecutor;

  private int indexOfFirstResult = 0;

  @Override
  public void executeImport() {
    int searchedSize;
    int maxPageSize = configurationService.getEngineImportMaxPageSize();
    ensureGreaterThanZero(maxPageSize);
    do {
      List<ENG> pageOfEngineEntities = queryEngineRestPoint(indexOfFirstResult, maxPageSize);
      List<ENG> newEngineEntities =
        getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
      if (!newEngineEntities.isEmpty()) {
        List<OPT> newOptimizeEntities = mapToOptimizeDto(newEngineEntities);
        importToElasticSearch(newOptimizeEntities);
      }
      searchedSize = pageOfEngineEntities.size();
      indexOfFirstResult += searchedSize;
    } while (searchedSize > 0);
  }

  public void resetImportStartIndex() {
    this.indexOfFirstResult = 0;
  }

  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize);

  /**
   * @return Finder that checks which entries are already in
   * imported to optimize.
   */
  protected abstract MissingEntitiesFinder<ENG> getMissingEntitiesFinder();

  /**
   * maps the entities from an engine representation
   * to an optimize representation.
   */
  protected abstract List<OPT> mapToOptimizeDto(List<ENG> entries);

  /**
   * imports the given events to optimize by
   * adding them to elasticsearch.
   */
  protected abstract void importToElasticSearch(List<OPT> events);

}
