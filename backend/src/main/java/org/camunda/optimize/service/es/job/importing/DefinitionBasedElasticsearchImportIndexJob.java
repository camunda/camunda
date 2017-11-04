package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.es.writer.DefinitionBasedImportIndexWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefinitionBasedElasticsearchImportIndexJob extends ElasticsearchImportJob<OptimizeDto> {

  private final Logger logger = LoggerFactory.getLogger(DefinitionBasedElasticsearchImportIndexJob.class);
  private DefinitionBasedImportIndexWriter importIndexWriter;
  private DefinitionBasedImportIndexDto importIndex;
  private String typeIndexComesFrom;

  public DefinitionBasedElasticsearchImportIndexJob(DefinitionBasedImportIndexWriter importIndexWriter,
                                                    DefinitionBasedImportIndexDto importIndex, String typeIndexComesFrom) {
    this.importIndexWriter = importIndexWriter;
    this.importIndex = importIndex;
    this.typeIndexComesFrom = typeIndexComesFrom;
  }

  @Override
  protected void executeImport() {
    try {
      importIndexWriter.importIndex(importIndex, typeIndexComesFrom);
    } catch (Exception e) {
      logger.error("error while writing definition based import index to elasticsearch", e);
    }
  }
}
