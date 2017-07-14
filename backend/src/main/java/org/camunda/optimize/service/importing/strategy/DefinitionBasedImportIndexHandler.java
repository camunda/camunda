package org.camunda.optimize.service.importing.strategy;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DefinitionBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;
import org.camunda.optimize.service.es.reader.DefinitionBasedImportIndexReader;
import org.camunda.optimize.service.es.writer.DefinitionBasedImportIndexWriter;
import org.camunda.optimize.service.importing.ImportJobExecutor;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DefinitionBasedImportIndexHandler implements ImportStrategy {

  private Logger logger = LoggerFactory.getLogger(DefinitionBasedImportIndexHandler.class);

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

  private List<DefinitionImportInformation> processDefinitionsToImport;
  private Set<DefinitionImportInformation> alreadyImportedProcessDefinitions;
  private String currentProcessDefinitionId = "";

  private int totalEntitiesImported;
  private int currentDefinitionBasedImportIndex;
  private int maxImportSize;
  private String elasticsearchType;
  private boolean initialized = false;

  @Override
  public void initializeImportIndex(String elasticsearchType, int maxPageSize) {
    this.elasticsearchType = elasticsearchType;
    this.maxImportSize = maxPageSize;
    loadImportDefaults();

    DefinitionBasedImportIndexDto dto = importIndexReader.getImportIndex(elasticsearchType);
    if (dto.getTotalEntitiesImported() > 0) {
      currentProcessDefinitionId = dto.getCurrentProcessDefinition();
      alreadyImportedProcessDefinitions = new HashSet<>(dto.getAlreadyImportedProcessDefinitions());
      processDefinitionsToImport.removeAll(alreadyImportedProcessDefinitions);
      currentDefinitionBasedImportIndex = dto.getCurrentDefinitionBasedImportIndex();
      totalEntitiesImported = dto.getTotalEntitiesImported();
    }
  }

  private void resetTotalEntitiesImported() {
    totalEntitiesImported = 0;
  }

  private void moveToNextDefinitionToImport() {
    DefinitionImportInformation definitionImportInformation = removeFirstItemFromProcessDefinitionsToImport();
    currentProcessDefinitionId = definitionImportInformation.getProcessDefinitionId();
    currentDefinitionBasedImportIndex = definitionImportInformation.getDefinitionBasedImportIndex();
  }

  private DefinitionImportInformation removeFirstItemFromProcessDefinitionsToImport() {
    DefinitionImportInformation result;
    if (!processDefinitionsToImport.isEmpty()) {
      result = processDefinitionsToImport.remove(0);
    } else {
      result = new DefinitionImportInformation();
      result.setProcessDefinitionId(currentProcessDefinitionId);
      result.setDefinitionBasedImportIndex(currentDefinitionBasedImportIndex);
    }
    return result;
  }

  public void makeSureIsInitialized() {
    if (!initialized) {
      initializeDefinitions();
      moveToNextDefinitionToImport();
    }
  }

  private void initializeDefinitions() {
    List<DefinitionImportInformation> processDefinitionsToImport = retrieveDefinitionsToImport();
    this.initialized = !processDefinitionsToImport.isEmpty();
    this.processDefinitionsToImport = processDefinitionsToImport;
  }

  private List<DefinitionImportInformation> retrieveDefinitionsToImport() {
    List<DefinitionImportInformation> processDefinitionsToImport =
      retrieveDefinitionsToImportFromConfigurationProvidedList();
    if (processDefinitionsToImport.isEmpty()) {
      processDefinitionsToImport = retrieveDefinitionToImportFromEngine();
    }
    return processDefinitionsToImport;
  }

  private List<DefinitionImportInformation> retrieveDefinitionsToImportFromConfigurationProvidedList() {
    List<DefinitionImportInformation> processDefinitionsToImport = new ArrayList<>();
    List<String> configuredProcessDefinitions =
        new ArrayList<>(Arrays.asList(configurationService.getProcessDefinitionsToImportAsArray()));

    for (String configuredProcessDefinition : configuredProcessDefinitions) {
      DefinitionImportInformation definitionImportInformation =
        new DefinitionImportInformation();
      definitionImportInformation.setDefinitionBasedImportIndex(0);
      definitionImportInformation.setProcessDefinitionId(configuredProcessDefinition);
      processDefinitionsToImport.add(definitionImportInformation);
    }
    return processDefinitionsToImport;
  }

  private List<DefinitionImportInformation> retrieveDefinitionToImportFromEngine() {
    ArrayList<DefinitionImportInformation> processDefinitionsToImport = new ArrayList<>();
    int currentStart = 0;
    List<ProcessDefinitionEngineDto> currentPage = engineEntityFetcher.fetchProcessDefinitions(currentStart, maxImportSize);

    while (currentPage != null && !currentPage.isEmpty()) {
      for (ProcessDefinitionEngineDto dto : currentPage) {
        DefinitionImportInformation definitionImportInformation =
          new DefinitionImportInformation();
        definitionImportInformation.setDefinitionBasedImportIndex(0);
        definitionImportInformation.setProcessDefinitionId(dto.getId());
        processDefinitionsToImport.add(definitionImportInformation);
      }
      currentStart = currentStart + currentPage.size();
      currentPage = engineEntityFetcher.fetchProcessDefinitions(currentStart, maxImportSize);
    }
    return processDefinitionsToImport;
  }

  @Override
  public boolean adjustIndexWhenNoResultsFound(boolean engineHasNewData) {
    if (!processDefinitionsToImport.isEmpty() && !engineHasNewData) {
      DefinitionImportInformation importDefinitionInformation =
        new DefinitionImportInformation();
      importDefinitionInformation.setDefinitionBasedImportIndex(currentDefinitionBasedImportIndex);
      importDefinitionInformation.setProcessDefinitionId(currentProcessDefinitionId);
      alreadyImportedProcessDefinitions.add(importDefinitionInformation);
      moveToNextDefinitionToImport();
      engineHasNewData = true;
    }
    return engineHasNewData;
  }

  @Override
  public void moveImportIndex(int units) {
    currentDefinitionBasedImportIndex += units;
    totalEntitiesImported += units;
  }

  @Override
  public int getRelativeImportIndex() {
    return currentDefinitionBasedImportIndex;
  }

  @Override
  public int getAbsoluteImportIndex() {
    return totalEntitiesImported;
  }

  @Override
  public void persistImportIndexToElasticsearch() {
    DefinitionBasedImportIndexDto dto = new DefinitionBasedImportIndexDto();
    dto.setTotalEntitiesImported(totalEntitiesImported);
    dto.setCurrentDefinitionBasedImportIndex(currentDefinitionBasedImportIndex);
    dto.setCurrentProcessDefinition(currentProcessDefinitionId);
    dto.setAlreadyImportedProcessDefinitions(new ArrayList<>(alreadyImportedProcessDefinitions));
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
    loadImportDefaults();
    persistImportIndexToElasticsearch();
  }

  public String getCurrentProcessDefinitionId() {
    return currentProcessDefinitionId;
  }

  public int getCurrentDefinitionBasedImportIndex() {
    return currentDefinitionBasedImportIndex;
  }

  public List<String> getAllProcessDefinitions() {
    ArrayList<String> result = new ArrayList<>();
    for (DefinitionImportInformation definitionImportInformation : processDefinitionsToImport) {
      result.add(definitionImportInformation.getProcessDefinitionId());
    }
    result.add(currentProcessDefinitionId);
    for (DefinitionImportInformation alreadyImportedProcessDefinition : alreadyImportedProcessDefinitions) {
      result.add(alreadyImportedProcessDefinition.getProcessDefinitionId());
    }
    return result;
  }

  public void loadImportDefaults() {
    initializeDefinitions();
    resetAlreadyImportedProcessDefinitions();
    resetTotalEntitiesImported();
    moveToNextDefinitionToImport();
  }

  public void updateDefinitionsToImportFromEngine() {
    addPossiblyNewDefinitionsFromEngineToImportList();
  }

  private void resetAlreadyImportedProcessDefinitions() {
    alreadyImportedProcessDefinitions = new HashSet<>(processDefinitionsToImport.size());
  }

  /**
   * Resets the process definitions to import, but keeps the relative indexes
   * from every respective process definition. Thus, we are not importing
   * all the once again, but starting from the last point we stopped at.
   */
  public void restartDefinitionBasedImportCycle() {
    if(processDefinitionsToImport.isEmpty()) {
      processDefinitionsToImport.addAll(alreadyImportedProcessDefinitions);
      addPossiblyNewDefinitionsFromEngineToImportList();
      alreadyImportedProcessDefinitions = new HashSet<>();
    }
  }

  private void addPossiblyNewDefinitionsFromEngineToImportList(){
    List<DefinitionImportInformation> engineList = retrieveDefinitionsToImport();
    for (DefinitionImportInformation definition : engineList) {
      if(!processDefinitionsToImport.contains(definition) &&
        !currentProcessDefinitionId.equals(definition.getProcessDefinitionId())) {
        processDefinitionsToImport.add(definition);
      }
    }
  }
}
