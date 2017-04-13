package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.job.impl.ImportIndexImportJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.ensureGreaterThanZero;

@Component
abstract class PaginatedImportService<ENG extends EngineDto, OPT extends OptimizeDto> implements ImportService {

  protected Logger logger = LoggerFactory.getLogger(PaginatedImportService.class);

  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected EngineEntityFetcher engineEntityFetcher;
  @Autowired
  protected ImportJobExecutor importJobExecutor;
  @Autowired
  private ImportIndexWriter importIndexWriter;
  @Autowired
  private ImportIndexReader importIndexReader;

  private int importIndex;

  @PostConstruct
  private void init() {
    importIndex = importIndexReader.getImportIndex(getElasticsearchType());
  }

  @Override
  public int executeImport() {
    int pagesWithData = 0;
    int searchedSize;
    int maxPageSize = configurationService.getEngineImportMaxPageSize();
    ensureGreaterThanZero(maxPageSize);
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

    return pagesWithData;
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

  /**
   * This is used for doc id when persisting the import index.
   * @return returns the type where the data is stored in elasticsearch.
   */
  protected abstract String getElasticsearchType();

}
