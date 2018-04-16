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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

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

  private LinkedList<DefinitionBasedImportPage> processDefinitionsToImport = new LinkedList<>();

  private DefinitionBasedImportPage lastPage;
  private DefinitionBasedImportPage currentPage = new ProcessDefinitionImportPageNotAvailable();
  private boolean allDefinitionsWithoutNewData = false;

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
    currentPage.setTimestampOfLastEntity(timestamp);
    allDefinitionsWithoutNewData = false;
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
      processDefinitionsToImport = loadedImportIndex.getProcessDefinitionsToImport();
      currentPage = loadedImportIndex.getCurrentProcessDefinition();
      lastPage = processDefinitionsToImport.isEmpty()? currentPage: processDefinitionsToImport.peekLast();
    }
  }

  @Override
  public DefinitionBasedImportPage getNextPage() {
    DefinitionBasedImportPage page = new DefinitionBasedImportPage();
    page.setTimestampOfLastEntity(currentPage.getTimestampOfLastEntity());
    page.setProcessDefinitionId(currentPage.getProcessDefinitionId());
    return page;
  }

  public void moveToNextDefinitionToImport() {
    if (currentPage == lastPage) {
      allDefinitionsWithoutNewData = true;
      addPossiblyNewDefinitionsFromEngineToImportList();
    }
    DefinitionBasedImportPage result = processDefinitionsToImport.poll();
    if (result != null) {
      processDefinitionsToImport.add(currentPage);
      currentPage = result;
    }
  }

  private void initializeDefinitions() {
    this.processDefinitionsToImport = processDefinitionManager.getAvailableProcessDefinitions(engineContext);
    if(!processDefinitionsToImport.isEmpty()) {
      lastPage = processDefinitionsToImport.peekLast();
      currentPage = processDefinitionsToImport.poll();
    } else {
      currentPage = new ProcessDefinitionImportPageNotAvailable();
    }
  }

  @Override
  public DefinitionBasedImportIndexDto createIndexInformationForStoring() {
    DefinitionBasedImportIndexDto indexToStore = new DefinitionBasedImportIndexDto();
    indexToStore.setCurrentProcessDefinition(currentPage);
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
  public void executeAfterMaxBackoffIsReached() {
    logger.debug("Restarting import cycle for type [{}]", getElasticsearchType());
    allDefinitionsWithoutNewData = true;
    addPossiblyNewDefinitionsFromEngineToImportList();
    moveToNextDefinitionToImport();
  }

  private void loadImportDefaults() {
    allDefinitionsWithoutNewData = true;
    initializeDefinitions();
  }

  public void updateImportIndex() {
    executeAfterMaxBackoffIsReached();
  }

  @Override
  public EngineContext getEngineContext() {
    return engineContext;
  }

  private void addPossiblyNewDefinitionsFromEngineToImportList() {
    List<DefinitionBasedImportPage> engineList = processDefinitionManager.getAvailableProcessDefinitions(engineContext);
    for (DefinitionBasedImportPage definition : engineList) {
      boolean notImportedAndNew = !processDefinitionsToImport.contains(definition) &&
          !currentPage.getProcessDefinitionId().equals(definition.getProcessDefinitionId());
      if (notImportedAndNew && configurationService.areProcessDefinitionsToImportDefined()) {
        notImportedAndNew =
            configurationService.getProcessDefinitionIdsToImport().contains(definition.getProcessDefinitionId());
      }
      if (notImportedAndNew) {
        processDefinitionsToImport.add(definition);
      }
    }
    if (!processDefinitionsToImport.isEmpty()) {
      this.lastPage = processDefinitionsToImport.peekLast();
    }
  }

  public boolean finishedDefinitionRoundWithoutNewData() {
    return currentPage == lastPage && allDefinitionsWithoutNewData;
  }
}
