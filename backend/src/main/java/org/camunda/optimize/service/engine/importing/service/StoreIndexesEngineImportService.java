package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.es.job.importing.StoreIndexesElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write all information of the current import index to elasticsearch.
 * If optimized is restarted the import index can thus be restored again.
 */
public class StoreIndexesEngineImportService {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private ImportIndexWriter importIndexWriter;
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  public StoreIndexesEngineImportService(ImportIndexWriter importIndexWriter,
                                         ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.importIndexWriter = importIndexWriter;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  public void executeImport(CombinedImportIndexesDto importIndexesToStore) {
    StoreIndexesElasticsearchImportJob storeIndexesImportJob =
      new StoreIndexesElasticsearchImportJob(importIndexWriter, importIndexesToStore);
    try {
      elasticsearchImportJobExecutor.executeImportJob(storeIndexesImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

}
