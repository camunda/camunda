package org.camunda.optimize.service.importing.index;

import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.job.importing.ImportIndexImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AllEntitiesBasedImportIndexHandler implements ImportIndexHandler {

  private Logger logger = LoggerFactory.getLogger(AllEntitiesBasedImportIndexHandler.class);

  @Autowired
  private ImportIndexWriter importIndexWriter;

  @Autowired
  private ImportIndexReader importIndexReader;

  @Autowired
  private ImportJobExecutor importJobExecutor;

  private int totalEntitiesImported;
  private String elasticsearchType;
  private String engineAlias;

  @Override
  public void initializeImportIndex(String elasticsearchType, String engineAlias) {
    this.elasticsearchType = elasticsearchType;
    this.engineAlias = engineAlias;
    totalEntitiesImported = importIndexReader.getImportIndex(elasticsearchType);
  }

  @Override
  public boolean adjustIndexWhenNoResultsFound(boolean hasNewData) {
    return false;
  }

  @Override
  public void persistImportIndexToElasticsearch() {
    ImportIndexImportJob indexImportJob =
      new ImportIndexImportJob(importIndexWriter, totalEntitiesImported, elasticsearchType, engineAlias);
    try {
      importJobExecutor.executeImportJob(indexImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of import index!", e);
    }
  }

  @Override
  public void moveImportIndex(int units) {
    totalEntitiesImported += units;
  }

  @Override
  public int getRelativeImportIndex() {
    return totalEntitiesImported;
  }

  @Override
  public int getAbsoluteImportIndex() {
    return totalEntitiesImported;
  }

  @Override
  public void resetImportIndex() {
    totalEntitiesImported = 0;
    persistImportIndexToElasticsearch();
  }

  @Override
  public void makeSureIsInitialized() {
    // nothing to do here
  }

  @Override
  public void restartImportCycle() {
    totalEntitiesImported = 0;
  }

  @Override
  public void updateImportIndex() {
    // nothing to do here
  }
}
