package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.DefinitionBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.writer.DefinitionBasedImportIndexWriter;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefinitionBasedImportIndexJob extends ImportJob<OptimizeDto> {

  private final Logger logger = LoggerFactory.getLogger(DefinitionBasedImportIndexJob.class);
  private DefinitionBasedImportIndexWriter importIndexWriter;
  private DefinitionBasedImportIndexDto importIndex;
  private String typeIndexComesFrom;

  public DefinitionBasedImportIndexJob(DefinitionBasedImportIndexWriter importIndexWriter,
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
