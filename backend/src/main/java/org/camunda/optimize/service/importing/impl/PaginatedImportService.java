package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportResult;
import org.camunda.optimize.service.importing.job.impl.ImportIndexImportJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.ensureGreaterThanZero;

@Component
public abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto> extends AbstractImportService <ENG, OPT> {

  @Autowired
  private ImportIndexWriter importIndexWriter;
  @Autowired
  private ImportIndexReader importIndexReader;

  private int importIndex;
  private int maxPageSize;

  @PostConstruct
  protected void init() {
    importIndex = importIndexReader.getImportIndex(getElasticsearchType());
    maxPageSize = this.getEngineImportMaxPageSize();
    ensureGreaterThanZero(maxPageSize);
  }

  @Override
  public ImportResult executeImport() throws OptimizeException {
    ImportResult result = new ImportResult();
    int pagesWithData = 0;
    int searchedSize;
    logger.debug("Importing page with index starting from '" + importIndex +
      "' and max page size '" + maxPageSize + "' from type " + getElasticsearchType());

    List<ENG> pageOfEngineEntities = queryEngineRestPoint(importIndex, maxPageSize);
    List<ENG> newEngineEntities =
      getMissingEntitiesFinder().retrieveMissingEntities(pageOfEngineEntities);
    if (!newEngineEntities.isEmpty()) {
      pagesWithData = pagesWithData + 1;
      List<OPT> newOptimizeEntities = mapToOptimizeDto(newEngineEntities);
      importToElasticSearch(newOptimizeEntities);
    }
    searchedSize = pageOfEngineEntities.size();
    importIndex += searchedSize;
    persistIndexToElasticsearch(importIndex);

    result.setPagesPassed(pagesWithData);
    result.setIdsToFetch(getIdsForPostProcessing());
    return result;
  }

  protected int getEngineImportMaxPageSize() {
    return configurationService.getEngineImportMaxPageSize();
  }

  public int getImportStartIndex() {
    return this.importIndex;
  }

  public void resetImportStartIndex() {
    this.importIndex = 0;
    persistIndexToElasticsearch(importIndex);
  }

  /**
   * Persists the given index to elasticsearch, which is reused, when Optimize is started.
   *
   * @param importIndex index where the import should start the next time optimize is started
   */
  private void persistIndexToElasticsearch(int importIndex) {
    ImportIndexImportJob indexImportJob =
      new ImportIndexImportJob(importIndexWriter, importIndex, getElasticsearchType());
    try {
      importJobExecutor.executeImportJob(indexImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of import index!", e);
    }
  }

  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize) throws OptimizeException;

}
