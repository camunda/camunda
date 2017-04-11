package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportIndexImportJob extends ImportJob<OptimizeDto> {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexImportJob.class);
  private ImportIndexWriter importIndexWriter;
  private int importIndex;
  private String typeIndexComesFrom;

  public ImportIndexImportJob(ImportIndexWriter importIndexWriter, int importIndex, String typeIndexComesFrom) {
    this.importIndexWriter = importIndexWriter;
    this.importIndex = importIndex;
    this.typeIndexComesFrom = typeIndexComesFrom;
  }

  @Override
  protected void getAbsentAggregateInformation() throws OptimizeException {
    // nothing to do here
  }

  @Override
  protected void executeImport() {
    try {
      importIndexWriter.importIndex(importIndex, typeIndexComesFrom);
    } catch (Exception e) {
      logger.error("error while writing import index to elasticsearch", e);
    }
  }
}
