package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.TotalQuantityEngineEntityFetcher;
import org.camunda.optimize.service.importing.job.impl.ImportIndexImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TotalQuantityBasedImportStrategy implements ImportStrategy {

  private Logger logger = LoggerFactory.getLogger(TotalQuantityBasedImportStrategy.class);

  @Autowired
  private ImportIndexWriter importIndexWriter;
  @Autowired
  private ImportIndexReader importIndexReader;
  @Autowired
  private ImportJobExecutor importJobExecutor;
  @Autowired
  private TotalQuantityEngineEntityFetcher engineEntityFetcher;

  private String elasticsearchType;
  private int importIndex;
  private int maxPageSize;

  @Override
  public void initializeImportIndex(String elasticsearchType, int maxPageSize) {
    this.elasticsearchType = elasticsearchType;
    this.maxPageSize = maxPageSize;
    importIndex = importIndexReader.getImportIndex(elasticsearchType);
  }

  @Override
  public int adjustIndexWhenNoResultsFound(int pagesWithData) {
    // nothing to do here
    return pagesWithData;
  }

  @Override
  public void persistImportIndexToElasticsearch() {
    ImportIndexImportJob indexImportJob =
      new ImportIndexImportJob(importIndexWriter, importIndex, elasticsearchType);
    try {
      importJobExecutor.executeImportJob(indexImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of import index!", e);
    }
  }

  @Override
  public void moveImportIndex(int units) {
    importIndex += units;
  }

  @Override
  public int getRelativeImportIndex() {
    return importIndex;
  }

  @Override
  public int getAbsoluteImportIndex() {
    return importIndex;
  }

  @Override
  public void resetImportIndex() {
    importIndex = 0;
    persistImportIndexToElasticsearch();
  }

  @Override
  public void updateConfigurationSettings() {
    // nothing to do here
  }

  @Override
  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances() {
    return engineEntityFetcher.fetchHistoricActivityInstances(importIndex, maxPageSize);
  }

  @Override
  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls() {
    return engineEntityFetcher.fetchProcessDefinitionXmls(importIndex, maxPageSize);
  }

  @Override
  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions() {
    return engineEntityFetcher.fetchProcessDefinitions(importIndex, maxPageSize);
  }

  @Override
  public Integer fetchHistoricActivityInstanceCount() throws OptimizeException {
    return engineEntityFetcher.fetchHistoricActivityInstanceCount();
  }

  @Override
  public Integer fetchProcessDefinitionCount() throws OptimizeException {
    return engineEntityFetcher.fetchProcessDefinitionCount();
  }
}
