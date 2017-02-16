package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingEntriesFinder;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto> implements ImportService {

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public void executeImport() {
    int resultSize;
    int indexOfFirstResult = 0;
    int maxPageSize = configurationService.getEngineImportMaxPageSize();
    do {
      List<ENG> newEngineEntities =
        getMissingEntriesFinder().retrieveMissingEntities(indexOfFirstResult, maxPageSize);
      if (!newEngineEntities.isEmpty()) {
        List<OPT> newOptimizeEntities = mapToOptimizeDto(newEngineEntities);
        importToElasticSearch(newOptimizeEntities);
      }
      resultSize = newEngineEntities.size();
      indexOfFirstResult += resultSize;
    } while (resultSize != 0);
  }

  protected abstract MissingEntriesFinder<ENG> getMissingEntriesFinder();

  protected abstract List<OPT> mapToOptimizeDto(List<ENG> entries);

  protected abstract void importToElasticSearch(List<OPT> events);

}
