package org.camunda.optimize.service.importing.job.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportIndexImportJob extends ImportJob<OptimizeDto> {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexImportJob.class);
  private ImportIndexWriter importIndexWriter;
  private int importIndex;
  private String typeIndexComesFrom;
  private String engine;

  public ImportIndexImportJob(ImportIndexWriter importIndexWriter, int importIndex, String typeIndexComesFrom, String engineAlias) {
    this.importIndexWriter = importIndexWriter;
    this.importIndex = importIndex;
    this.typeIndexComesFrom = typeIndexComesFrom;
    this.engine = engineAlias;
  }

  @Override
  protected void executeImport() {
    try {
      importIndexWriter.importIndex(importIndex, typeIndexComesFrom, engine);
    } catch (Exception e) {
      logger.error("error while writing import index to elasticsearch", e);
    }
  }
}
