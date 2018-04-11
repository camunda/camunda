package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionImportPageNotAvailable;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.ProcessDefinitionManager;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.es.reader.DefinitionBasedImportIndexReader;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class DefinitionBasedImportIndexHandler
  implements ImportIndexHandler<DefinitionBasedImportPage, DefinitionBasedImportIndexDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private DefinitionBasedImportIndexReader importIndexReader;
  @Autowired
  private ProcessDefinitionManager processDefinitionManager;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected BeanHelper beanHelper;

  private List<DefinitionBasedImportPage> processDefinitionsToImport = new ArrayList<>();
  private Set<DefinitionBasedImportPage> alreadyImportedProcessDefinitions = new HashSet<>();

  private DefinitionBasedImportPage currentIndex = new ProcessDefinitionImportPageNotAvailable();

  protected EngineContext engineContext;

  public DefinitionBasedImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  protected void init() {
    loadImportDefaults();
    readIndexFromElasticsearch();
  }

  public void updateIndexTimestamp(OffsetDateTime timestamp) {
    currentIndex.setTimestampOfLastEntity(timestamp);
  }

  /**
   * States the Elasticsearch type where the index information should be stored.
   */
  protected abstract String getElasticsearchType();

  @Override
  public void readIndexFromElasticsearch() {
    Optional<DefinitionBasedImportIndexDto> dto =
      importIndexReader.getImportIndex(getElasticsearchType(), engineContext.getEngineAlias());
    if (dto.isPresent()) {
      DefinitionBasedImportIndexDto loadedImportIndex = dto.get();
      alreadyImportedProcessDefinitions = new HashSet<>(loadedImportIndex.getAlreadyImportedProcessDefinitions());
      processDefinitionsToImport = loadedImportIndex.getProcessDefinitionsToImport();
      currentIndex = loadedImportIndex.getCurrentProcessDefinition();
    }
  }

  @Override
  public DefinitionBasedImportPage getNextPage() {
    DefinitionBasedImportPage page = new DefinitionBasedImportPage();
    page.setTimestampOfLastEntity(currentIndex.getTimestampOfLastEntity());
    page.setProcessDefinitionId(currentIndex.getProcessDefinitionId());
    return page;
  }

  private void resetCurrentIndex() {
    currentIndex = new ProcessDefinitionImportPageNotAvailable();
  }

  public void moveToNextDefinitionToImport() {
    if (hasStillNewDefinitionsToImport()) {
      if (!(currentIndex instanceof ProcessDefinitionImportPageNotAvailable)) {
        alreadyImportedProcessDefinitions.add(currentIndex);
      }
      currentIndex = removeFirstItemFromProcessDefinitionsToImport();
    }
  }

  private DefinitionBasedImportPage removeFirstItemFromProcessDefinitionsToImport() {
    DefinitionBasedImportPage result;
    if (hasStillNewDefinitionsToImport()) {
      result = processDefinitionsToImport.remove(0);
    } else {
      result = currentIndex;
    }
    return result;
  }

  public boolean hasStillNewDefinitionsToImport() {
    return !processDefinitionsToImport.isEmpty();
  }

  private void initializeDefinitions() {
    this.processDefinitionsToImport = processDefinitionManager.getAvailableProcessDefinitions(engineContext);
  }

  @Override
  public DefinitionBasedImportIndexDto createIndexInformationForStoring() {
    DefinitionBasedImportIndexDto indexToStore = new DefinitionBasedImportIndexDto();
    indexToStore.setCurrentProcessDefinition(currentIndex);
    indexToStore.setAlreadyImportedProcessDefinitions(new ArrayList<>(alreadyImportedProcessDefinitions));
    indexToStore.setProcessDefinitionsToImport(processDefinitionsToImport);
    indexToStore.setEngine(this.engineContext.getEngineAlias());
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchType());
    return indexToStore;
  }

  @Override
  public void resetImportIndex() {
    loadImportDefaults();
  }

  /**
   * Resets the process definitions to import, but keeps the last timestamps
   * for every respective process definition. Thus, we are not importing
   * all the once again, but starting from the last point we stopped at.
   */
  public void restartImportCycle() {
    if(!hasStillNewDefinitionsToImport()) {
      logger.debug("Restarting import cycle for type [{}]", getElasticsearchType());
      processDefinitionsToImport.addAll(alreadyImportedProcessDefinitions);
      addPossiblyNewDefinitionsFromEngineToImportList();
      alreadyImportedProcessDefinitions = new HashSet<>();
    }
  }

  private void loadImportDefaults() {
    initializeDefinitions();
    resetAlreadyImportedProcessDefinitions();
    resetCurrentIndex();
    moveToNextDefinitionToImport();
  }

  private void resetAlreadyImportedProcessDefinitions() {
    alreadyImportedProcessDefinitions = new HashSet<>(processDefinitionsToImport.size());
  }

  public void updateImportIndex() {
    addPossiblyNewDefinitionsFromEngineToImportList();
  }

  private void addPossiblyNewDefinitionsFromEngineToImportList() {
    List<DefinitionBasedImportPage> engineList = processDefinitionManager.getAvailableProcessDefinitions(engineContext);
    for (DefinitionBasedImportPage definition : engineList) {
      boolean notImportedAndNew = !processDefinitionsToImport.contains(definition) &&
          !currentIndex.getProcessDefinitionId().equals(definition.getProcessDefinitionId()) &&
          !new ArrayList<>(alreadyImportedProcessDefinitions).contains(definition);
      if (notImportedAndNew && configurationService.areProcessDefinitionsToImportDefined()) {
        notImportedAndNew =
            configurationService.getProcessDefinitionIdsToImport().contains(definition.getProcessDefinitionId());
      }
      if (notImportedAndNew) {
        processDefinitionsToImport.add(definition);
      }
    }
  }

  @Override
  public EngineContext getEngineContext() {
    return engineContext;
  }
}
