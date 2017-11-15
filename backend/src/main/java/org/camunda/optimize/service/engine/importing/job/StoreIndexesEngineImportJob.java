package org.camunda.optimize.service.engine.importing.job;

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
public class StoreIndexesEngineImportJob implements Runnable {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private CombinedImportIndexesDto importIndexesToStore;
  private ImportIndexWriter importIndexWriter;
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  public StoreIndexesEngineImportJob(CombinedImportIndexesDto importIndexesToStore,
                                     ImportIndexWriter importIndexWriter,
                                     ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.importIndexesToStore = importIndexesToStore;
    this.importIndexWriter = importIndexWriter;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  @Override
  public void run() {
    StoreIndexesElasticsearchImportJob storeIndexesImportJob =
      new StoreIndexesElasticsearchImportJob(importIndexWriter, importIndexesToStore);
    try {
      elasticsearchImportJobExecutor.executeImportJob(storeIndexesImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

}
