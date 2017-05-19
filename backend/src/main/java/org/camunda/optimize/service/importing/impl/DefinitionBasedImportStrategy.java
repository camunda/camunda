package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.es.reader.DefinitionBasedImportIndexReader;
import org.camunda.optimize.service.es.writer.DefinitionBasedImportIndexWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportStrategy;
import org.camunda.optimize.service.importing.fetcher.DefinitionBasedEngineEntityFetcher;
import org.camunda.optimize.service.importing.job.importing.DefinitionBasedImportIndexJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DefinitionBasedImportStrategy implements ImportStrategy {

  private Logger logger = LoggerFactory.getLogger(DefinitionBasedImportStrategy.class);

  @Autowired
  private DefinitionBasedImportIndexWriter importIndexWriter;
  @Autowired
  private DefinitionBasedImportIndexReader importIndexReader;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private DefinitionBasedEngineEntityFetcher engineEntityFetcher;
  @Autowired
  private ImportJobExecutor importJobExecutor;

  private List<String> processDefinitionsToImport;
  private List<String> alreadyImportProcessDefinitions;
  private String currentProcessDefinition;

  private int totalEntitiesImported;
  private int importIndex;
  private int maxImportSize;
  private String elasticsearchType;

  @Override
  public void initializeImportIndex(String elasticsearchType, int maxPageSize) {
    this.elasticsearchType = elasticsearchType;
    this.maxImportSize = maxPageSize;
    updateProcessDefinitionRelatedInformation();
    importIndex = 0;

    DefinitionBasedImportIndexDto dto = importIndexReader.getImportIndex(elasticsearchType);
    if (dto.getImportIndex() > 0) {
      currentProcessDefinition = dto.getCurrentProcessDefinition();
      alreadyImportProcessDefinitions = dto.getAlreadyImportedProcessDefinitions();
      importIndex = dto.getImportIndex();
      totalEntitiesImported = dto.getTotalEntitiesImported();
    }
  }

  @Override
  public void updateConfigurationSettings() {
    updateProcessDefinitionRelatedInformation();
  }

  private void updateProcessDefinitionRelatedInformation() {
    processDefinitionsToImport = getProcessDefinitionsToImport();
    alreadyImportProcessDefinitions = new ArrayList<>(processDefinitionsToImport.size());
    currentProcessDefinition = removeFirstItemFromProcessDefinitionsToImport();
  }

  private String removeFirstItemFromProcessDefinitionsToImport() {
    return processDefinitionsToImport.remove(0);
  }

  private List<String> getProcessDefinitionsToImport() {
    String[] procDefs = configurationService.getProcessDefinitionsToImportAsArray();
    ensureNotEmpty(procDefs);
    List<String> allProcDefsToImport = new LinkedList<>();
    Collections.addAll(allProcDefsToImport, procDefs);
    return allProcDefsToImport;
  }

  @Override
  public int adjustIndexWhenNoResultsFound(int pagesWithData) {
    if (!processDefinitionsToImport.isEmpty()) {
      alreadyImportProcessDefinitions.add(currentProcessDefinition);
      currentProcessDefinition = removeFirstItemFromProcessDefinitionsToImport();
      importIndex = 0;
      pagesWithData = pagesWithData + 1;
      persistImportIndexToElasticsearch();
    }
    return pagesWithData;
  }

  @Override
  public void moveImportIndex(int units) {
    importIndex += units;
    totalEntitiesImported += units;
  }

  @Override
  public int getRelativeImportIndex() {
    return importIndex;
  }

  @Override
  public int getAbsoluteImportIndex() {
    return totalEntitiesImported;
  }

  @Override
  public void persistImportIndexToElasticsearch() {
    DefinitionBasedImportIndexDto dto = new DefinitionBasedImportIndexDto();
    dto.setTotalEntitiesImported(totalEntitiesImported);
    dto.setImportIndex(importIndex);
    dto.setCurrentProcessDefinition(currentProcessDefinition);
    dto.setAlreadyImportedProcessDefinitions(alreadyImportProcessDefinitions);
    DefinitionBasedImportIndexJob indexImportJob =
        new DefinitionBasedImportIndexJob(importIndexWriter, dto, elasticsearchType);
    try {
      importJobExecutor.executeImportJob(indexImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of definition based import index!", e);
    }
  }

  @Override
  public void resetImportIndex() {
    updateProcessDefinitionRelatedInformation();
    importIndex = 0;
    totalEntitiesImported = 0;
    persistImportIndexToElasticsearch();
  }

  @Override
  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances() {
    return engineEntityFetcher.fetchHistoricActivityInstances(importIndex, maxImportSize, currentProcessDefinition);
  }

  @Override
  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls() {
    return engineEntityFetcher.fetchProcessDefinitionXml(importIndex, maxImportSize, currentProcessDefinition);
  }

  @Override
  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions() {
    return engineEntityFetcher.fetchProcessDefinitions(importIndex, maxImportSize, currentProcessDefinition);
  }

  @Override
  public Integer fetchHistoricActivityInstanceCount() throws OptimizeException {
    return engineEntityFetcher.fetchHistoricActivityInstanceCount(getProcessDefinitionsToImport());
  }

  @Override
  public Integer fetchProcessDefinitionCount() throws OptimizeException {
    return engineEntityFetcher.fetchProcessDefinitionCount(getProcessDefinitionsToImport());
  }
}
