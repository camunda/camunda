package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoreIndexesElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private Logger logger = LoggerFactory.getLogger(StoreIndexesElasticsearchImportJob.class);

  private ImportIndexWriter importIndexWriter;
  private CombinedImportIndexesDto indexesToStore;

  public StoreIndexesElasticsearchImportJob(ImportIndexWriter importIndexWriter, CombinedImportIndexesDto indexesToStore) {
    this.importIndexWriter = importIndexWriter;
    this.indexesToStore = indexesToStore;
  }

  @Override
  protected void executeImport() {
    try {
      importIndexWriter.importIndexes(indexesToStore);
    } catch (Exception e) {
      logger.error("error while writing indexes to elasticsearch", e);
    }
  }
}
