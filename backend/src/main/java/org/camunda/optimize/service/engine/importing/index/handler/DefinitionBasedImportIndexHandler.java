package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionInformationNotAvailable;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.ProcessDefinitionManager;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.es.reader.DefinitionBasedImportIndexReader;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public abstract class DefinitionBasedImportIndexHandler
  extends BackoffImportIndexHandler<DefinitionBasedImportPage, DefinitionBasedImportIndexDto> {

  @Autowired
  protected DefinitionBasedImportIndexReader importIndexReader;
  @Autowired
  protected ProcessDefinitionManager processDefinitionManager;

  protected List<DefinitionImportInformation> processDefinitionsToImport = new ArrayList<>();
  protected Set<DefinitionImportInformation> alreadyImportedProcessDefinitions = new HashSet<>();

  protected DefinitionImportInformation currentIndex = new ProcessDefinitionInformationNotAvailable();
  protected Long totalEntitiesImported = 0L;

  protected EngineContext engineContext;

  public DefinitionBasedImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  protected void init() {
    resetCurrentIndex();
    loadImportDefaults();
    readIndexFromElasticsearch();
  }

  /**
   * Fetch the maximum number of entities that belong to that process definition.
   */
  protected abstract long fetchMaxEntityCountForDefinition(String processDefinitionId);

  /**
   * Retrieves the maximum page size that should be used for this index handler.
   */
  protected abstract long getMaxPageSize();

  /**
   * Fetches the maximum number of entities that belong to all process definitions.
   */
  protected abstract long fetchMaxEntityCountForAllDefinitions();

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
      totalEntitiesImported = loadedImportIndex.getTotalEntitiesImported();
    }
  }

  @Override
  public Optional<DefinitionBasedImportPage> getNextImportPage() {
    if (canCreateNewPage()) {
      DefinitionBasedImportPage page = new DefinitionBasedImportPage();
      page.setIndexOfFirstResult(currentIndex.getDefinitionBasedImportIndex());
      page.setCurrentProcessDefinitionId(currentIndex.getProcessDefinitionId());
      long nextPageSize = getNextPageSize();
      page.setPageSize(nextPageSize);
      moveImportIndex(nextPageSize);
      return Optional.of(page);
    } else {
      addPossiblyNewDefinitionsFromEngineToImportList();
      return Optional.empty();
    }
  }

  private long getNextPageSize() {
    long diff = currentIndex.getMaxEntityCount() - currentIndex.getDefinitionBasedImportIndex();
    long nextPageSize = Math.min(getMaxPageSize(), diff);
    nextPageSize = Math.max(0L, nextPageSize);
    return nextPageSize;
  }

  private boolean canCreateNewPage() {
    if (currentIndex.reachedMaxCount()) {
      updateMaxEntityCount();
      if (currentIndex.reachedMaxCount()) {
        moveToNextDefinitionToImport();
        return !currentIndex.reachedMaxCount();
      }
    }
    return true;
  }

  private void updateMaxEntityCount() {
    // if process definition id is empty and we fetch max count, then
    // the max count based on all process definition ids is fetched.
    if (currentIndex.hasValidProcessDefinitionId()) {
      currentIndex.setMaxEntityCount(fetchMaxEntityCountForDefinition(currentIndex.getProcessDefinitionId()));
    }
  }

  @Override
  public OptionalDouble computeProgress() {
    long totalEntityCount = 0;
    try {
      totalEntityCount = fetchMaxEntityCountForAllDefinitions();
    } catch (Exception e) {
      //nothing to do error has been already reported before
    }
    long totalEntitiesImported = this.totalEntitiesImported;

    boolean hasNothingToImport = totalEntityCount == 0L;
    boolean allEntitiesHaveBeenImported = totalEntitiesImported >= totalEntityCount;
    if (hasNothingToImport) {
      return OptionalDouble.empty();
    } else if (allEntitiesHaveBeenImported) {
      return OptionalDouble.of(100.0);
    } else {
      Long maxCount = Math.max(1L, totalEntityCount);
      return OptionalDouble.of(totalEntitiesImported / maxCount.doubleValue() * 100.0);
    }
  }

  private void resetTotalEntitiesImported() {
    totalEntitiesImported = 0L;
  }

  private void resetCurrentIndex() {
    currentIndex = new ProcessDefinitionInformationNotAvailable();
  }

  private void moveToNextDefinitionToImport() {
    if (hasStillNewDefinitionsToImport()) {
      if (!(currentIndex instanceof ProcessDefinitionInformationNotAvailable)) {
        alreadyImportedProcessDefinitions.add(currentIndex);
      }
      currentIndex = removeFirstItemFromProcessDefinitionsToImport();
      updateMaxEntityCount();
    }
  }

  private DefinitionImportInformation removeFirstItemFromProcessDefinitionsToImport() {
    DefinitionImportInformation result;
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
    List<DefinitionImportInformation> processDefinitionsToImport = processDefinitionManager.getAvailableProcessDefinitions(engineContext);
    this.processDefinitionsToImport = processDefinitionsToImport;
  }

  public void moveImportIndex(long units) {
    currentIndex.moveImportIndex(units);
    totalEntitiesImported += units;
  }

  @Override
  public DefinitionBasedImportIndexDto createIndexInformationForStoring() {
    DefinitionBasedImportIndexDto indexToStore = new DefinitionBasedImportIndexDto();
    indexToStore.setTotalEntitiesImported(totalEntitiesImported);
    indexToStore.setCurrentProcessDefinition(currentIndex);
    indexToStore.setAlreadyImportedProcessDefinitions(new ArrayList<>(alreadyImportedProcessDefinitions));
    indexToStore.setProcessDefinitionsToImport(processDefinitionsToImport);
    indexToStore.setEngine(this.engineContext.getEngineAlias());
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchType());
    return indexToStore;
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    loadImportDefaults();
  }

  /**
   * Resets the process definitions to import, but keeps the relative indexes
   * from every respective process definition. Thus, we are not importing
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

  public Long getCurrentDefinitionBasedImportIndex() {
    return currentIndex.getDefinitionBasedImportIndex();
  }

  public List<String> getAllProcessDefinitions() {
    Set<String> result = new HashSet<>();
    for (DefinitionImportInformation definitionImportInformation : processDefinitionsToImport) {
      result.add(definitionImportInformation.getProcessDefinitionId());
    }
    result.add(currentIndex.getProcessDefinitionId());
    for (DefinitionImportInformation alreadyImportedProcessDefinition : alreadyImportedProcessDefinitions) {
      result.add(alreadyImportedProcessDefinition.getProcessDefinitionId());
    }
    return new ArrayList<>(result);
  }

  public void loadImportDefaults() {
    initializeDefinitions();
    resetAlreadyImportedProcessDefinitions();
    resetCurrentIndex();
    resetTotalEntitiesImported();
    moveToNextDefinitionToImport();
  }

  private void resetAlreadyImportedProcessDefinitions() {
    alreadyImportedProcessDefinitions = new HashSet<>(processDefinitionsToImport.size());
  }

  public void updateImportIndex() {
    addPossiblyNewDefinitionsFromEngineToImportList();
  }

  private void addPossiblyNewDefinitionsFromEngineToImportList() {
    List<DefinitionImportInformation> engineList = processDefinitionManager.getAvailableProcessDefinitions(engineContext);
    for (DefinitionImportInformation definition : engineList) {
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
