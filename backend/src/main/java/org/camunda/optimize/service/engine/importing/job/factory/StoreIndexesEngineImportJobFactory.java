package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.job.StoreIndexesEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StoreIndexesEngineImportJobFactory
    extends EngineImportJobFactoryImpl {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ImportIndexWriter importIndexWriter;

  private LocalDateTime dateUntilJobCreationIsBlocked;
  protected EngineContext engineContext;

  public StoreIndexesEngineImportJobFactory(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
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
      try {
        return Optional.of(createStoreIndexJob());
      } catch (Exception e) {
        logger.error("Could not create import job for storing index information!", e);
        return Optional.empty();
      }
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

    if (provider.getAllEntitiesBasedHandlers(engineContext.getEngineAlias()) != null) {
      for (AllEntitiesBasedImportIndexHandler importIndexHandler :
          provider.getAllEntitiesBasedHandlers(engineContext.getEngineAlias())) {
        allEntitiesBasedImportIndexes.add(importIndexHandler.createIndexInformationForStoring());
      }

      provider
        .getAllScrollBasedHandlers(engineContext.getEngineAlias())
        .forEach(handler -> allEntitiesBasedImportIndexes.add(handler.createIndexInformationForStoring()));
    }

    return allEntitiesBasedImportIndexes;
  }

  private List<DefinitionBasedImportIndexDto> getDefinitionBasedImportIndexes() {
    List<DefinitionBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();

    if (provider.getDefinitionBasedHandlers(engineContext.getEngineAlias()) != null) {
      for (DefinitionBasedImportIndexHandler importIndexHandler :
          provider.getDefinitionBasedHandlers(engineContext.getEngineAlias())) {
        allEntitiesBasedImportIndexes.add(importIndexHandler.createIndexInformationForStoring());
      }
    }

    return allEntitiesBasedImportIndexes;
  }

  public void disableBlocking() {
    this.dateUntilJobCreationIsBlocked = LocalDateTime.MIN;
  }
}
