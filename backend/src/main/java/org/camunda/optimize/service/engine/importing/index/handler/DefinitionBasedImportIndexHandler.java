package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionInformationNotAvailable;
import org.camunda.optimize.dto.optimize.importing.VersionedDefinitionImportInformation;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.es.reader.DefinitionBasedImportIndexReader;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class DefinitionBasedImportIndexHandler
  extends BackoffImportIndexHandler<DefinitionBasedImportPage, DefinitionBasedImportIndexDto> {

  @Autowired
  protected DefinitionBasedImportIndexReader importIndexReader;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected ProcessDefinitionFetcher engineEntityFetcher;

  protected List<DefinitionImportInformation> processDefinitionsToImport = new ArrayList<>();
  protected Set<DefinitionImportInformation> alreadyImportedProcessDefinitions = new HashSet<>();

  protected DefinitionImportInformation currentIndex = new ProcessDefinitionInformationNotAvailable();
  protected Long totalEntitiesImported = 0L;

  protected String engineAlias = "1";

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
      importIndexReader.getImportIndex(getElasticsearchType(), engineAlias);
    if (dto.isPresent()) {
      DefinitionBasedImportIndexDto loadedImportIndex = dto.get();
      alreadyImportedProcessDefinitions = new HashSet<>(loadedImportIndex.getAlreadyImportedProcessDefinitions());
      processDefinitionsToImport.removeAll(loadedImportIndex.getAlreadyImportedProcessDefinitions());
      processDefinitionsToImport.remove(loadedImportIndex.getCurrentProcessDefinition());
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
    long totalEntityCount = fetchMaxEntityCountForAllDefinitions();
    boolean hasNothingToImport = totalEntityCount == 0L;
    boolean allEntitiesHaveBeenImported = totalEntitiesImported >= totalEntityCount;
    if (hasNothingToImport) {
      return OptionalDouble.empty();
    } else if (allEntitiesHaveBeenImported) {
      return OptionalDouble.of(100.0);
    } else {
      Long maxCount = Math.max(1L, totalEntityCount);
      return OptionalDouble.of(totalEntitiesImported.doubleValue() / maxCount.doubleValue() * 100.0);
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
    List<DefinitionImportInformation> processDefinitionsToImport = retrieveDefinitionsToImport();
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
    List<String> configuredProcessDefinitionIds =
      configurationService.getProcessDefinitionIdsToImport();

    for (String configuredProcessDefinitionId : configuredProcessDefinitionIds) {
      DefinitionImportInformation definitionImportInformation =
        new DefinitionImportInformation();
      definitionImportInformation.setDefinitionBasedImportIndex(0);
      definitionImportInformation.setMaxEntityCount(0);
      definitionImportInformation.setProcessDefinitionId(configuredProcessDefinitionId);
      processDefinitionsToImport.add(definitionImportInformation);
    }
    return processDefinitionsToImport;
  }

  private List<DefinitionImportInformation> retrieveDefinitionToImportFromEngine() {
    List<DefinitionImportInformation> result = new ArrayList<>();
    result.addAll(this.retrieveDefinitionToImportFromEngine(this.engineAlias));
    return result;
  }

  private List<DefinitionImportInformation> retrieveDefinitionToImportFromEngine(String engineAlias) {
    int currentStart = 0;
    long maxPageSize = configurationService.getEngineImportProcessDefinitionMaxPageSize();
    List<ProcessDefinitionEngineDto> currentPage = engineEntityFetcher.fetchProcessDefinitions(currentStart, maxPageSize);

    HashMap<String, TreeSet<VersionedDefinitionImportInformation>> versionSortedProcesses = new HashMap<>();
    while (currentPage != null && !currentPage.isEmpty()) {

      for (ProcessDefinitionEngineDto dto : currentPage) {
        VersionedDefinitionImportInformation definitionImportInformation =
          new VersionedDefinitionImportInformation();
        definitionImportInformation.setDefinitionBasedImportIndex(0);
        definitionImportInformation.setMaxEntityCount(0);
        definitionImportInformation.setProcessDefinitionId(dto.getId());
        definitionImportInformation.setVersion(dto.getVersion());

        if (!versionSortedProcesses.containsKey(dto.getKey())) {
          versionSortedProcesses.put(
              dto.getKey(),
              new TreeSet<>((o1, o2) -> Integer.compare(o2.getVersion(), o1.getVersion()))
          );
        }
        versionSortedProcesses.get(dto.getKey()).add(definitionImportInformation);
      }
      currentStart = currentStart + currentPage.size();
      currentPage = engineEntityFetcher.fetchProcessDefinitions(currentStart, maxPageSize);
    }
    List<DefinitionImportInformation> result = buildSortedOrder(versionSortedProcesses);
    // transform to unversioned definition import information. Otherwise we have later
    // problems to store the information to Elasticsearch.
    result = result
      .stream()
      .map(DefinitionImportInformation::copy)
      .collect(Collectors.toList());
    return result;
  }

  private List<DefinitionImportInformation> buildSortedOrder(HashMap<String, TreeSet<VersionedDefinitionImportInformation>> processDefinitionsToImport) {
    ArrayList<DefinitionImportInformation> result = new ArrayList<>();
    while (!processDefinitionsToImport.isEmpty()) {
      Iterator<Map.Entry<String, TreeSet<VersionedDefinitionImportInformation>>> iterator =
          processDefinitionsToImport.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<String,TreeSet<VersionedDefinitionImportInformation>> entry = iterator.next();
        if (!entry.getValue().isEmpty()) {
          result.add(entry.getValue().pollFirst());
        } else {
          iterator.remove();
        }
      }
    }
    return result;
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
    indexToStore.setEngine(this.engineAlias);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchType());
    return indexToStore;
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    loadImportDefaults();
  }

  public String getCurrentProcessDefinitionId() {
    return currentIndex.getProcessDefinitionId();
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

  public void updateImportIndex() {
    addPossiblyNewDefinitionsFromEngineToImportList();
  }

  private void addPossiblyNewDefinitionsFromEngineToImportList(){
    List<DefinitionImportInformation> engineList = retrieveDefinitionsToImport();
    for (DefinitionImportInformation definition : engineList) {
      if(!processDefinitionsToImport.contains(definition) &&
        !currentIndex.getProcessDefinitionId().equals(definition.getProcessDefinitionId()) &&
        !new ArrayList<>(alreadyImportedProcessDefinitions).contains(definition)) {
        processDefinitionsToImport.add(definition);
      }
    }
  }
}
