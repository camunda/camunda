package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.job.StoreIndexesEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class StoreIndexesEngineImportJobFactory implements EngineImportJobFactory {

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ImportIndexHandlerProvider importIndexHandlerProvider;
  @Autowired
  private ImportIndexWriter importIndexWriter;

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private LocalDateTime dateUntilJobCreationIsBlocked;

  @PostConstruct
  public void init() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
  }

  @Override
  public void setElasticsearchImportExecutor(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  @Override
  public long getBackoffTimeInMs() {
    long backoffTime = LocalDateTime.now().until(dateUntilJobCreationIsBlocked, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  public Optional<Runnable> getNextJob() {
    if (LocalDateTime.now().isAfter(dateUntilJobCreationIsBlocked)) {
      dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
      return Optional.of(createStoreIndexJob());
    } else {
      return Optional.empty();
    }
  }

  private LocalDateTime calculateDateUntilJobCreationIsBlocked() {
    return LocalDateTime.now().plusSeconds(configurationService.getImportIndexAutoStorageIntervalInSec());
  }

  private Runnable createStoreIndexJob() {
    CombinedImportIndexesDto importIndexesToStore = new CombinedImportIndexesDto();
    importIndexesToStore.setAllEntitiesBasedImportIndexes(getAllEntitiesBasedImportIndexes());
    importIndexesToStore.setDefinitionBasedIndexes(getDefinitionBasedImportIndexes());
    StoreIndexesEngineImportJob importJob =
      new StoreIndexesEngineImportJob(importIndexesToStore, importIndexWriter, elasticsearchImportJobExecutor);
    return importJob;
  }

  private List<AllEntitiesBasedImportIndexDto> getAllEntitiesBasedImportIndexes() {
    List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();
    for (AllEntitiesBasedImportIndexHandler importIndexHandler : importIndexHandlerProvider.getAllEntitiesBasedHandlers()) {
      allEntitiesBasedImportIndexes.add(importIndexHandler.createIndexInformationForStoring());
    }
    return allEntitiesBasedImportIndexes;
  }

  private List<DefinitionBasedImportIndexDto> getDefinitionBasedImportIndexes() {
    List<DefinitionBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();
    for (DefinitionBasedImportIndexHandler importIndexHandler : importIndexHandlerProvider.getDefinitionBasedHandlers()) {
      allEntitiesBasedImportIndexes.add(importIndexHandler.createIndexInformationForStoring());
    }
    return allEntitiesBasedImportIndexes;
  }

  public void disableBlocking() {
    this.dateUntilJobCreationIsBlocked = LocalDateTime.MIN;
  }
}
