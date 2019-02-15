package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StoreIndexesElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private ImportIndexWriter importIndexWriter;
  private CombinedImportIndexesDto indexesToStore;

  public StoreIndexesElasticsearchImportJob(ImportIndexWriter importIndexWriter, CombinedImportIndexesDto indexesToStore) {

    super(() -> {});
    this.importIndexWriter = importIndexWriter;
    this.indexesToStore = indexesToStore;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> newOptimizeEntities) throws Exception {
    importIndexWriter.importIndexes(indexesToStore);
  }
}
