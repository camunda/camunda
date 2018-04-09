package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.service.StoreIndexesEngineImportService;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StoreIndexesEngineImportMediator
    implements EngineImportMediator {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ImportIndexWriter importIndexWriter;

  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected ImportIndexHandlerProvider provider;

  private StoreIndexesEngineImportService importService;

  private OffsetDateTime dateUntilJobCreationIsBlocked;
  protected EngineContext engineContext;

  public StoreIndexesEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    importService = new StoreIndexesEngineImportService(importIndexWriter, elasticsearchImportJobExecutor);
  }

  @Override
  public long getBackoffTimeInMs() {
    long backoffTime = OffsetDateTime.now().until(dateUntilJobCreationIsBlocked, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  @Override
  public void importNextPage() {
    if (canImport()) {
      dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
      try {
        CombinedImportIndexesDto importIndexes = createStoreIndexJob();
        importService.executeImport(importIndexes);
      } catch (Exception e) {
        logger.error("Could execute import for storing index information!", e);
      }
    }
  }

  @Override
  public boolean canImport() {
    return OffsetDateTime.now().isAfter(dateUntilJobCreationIsBlocked);
  }

  private OffsetDateTime calculateDateUntilJobCreationIsBlocked() {
    return OffsetDateTime.now().plusSeconds(configurationService.getImportIndexAutoStorageIntervalInSec());
  }

  private CombinedImportIndexesDto createStoreIndexJob() {
    CombinedImportIndexesDto importIndexesToStore = new CombinedImportIndexesDto();
    importIndexesToStore.setAllEntitiesBasedImportIndexes(getAllEntitiesBasedImportIndexes());
    importIndexesToStore.setDefinitionBasedIndexes(getDefinitionBasedImportIndexes());
    return importIndexesToStore;
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
    this.dateUntilJobCreationIsBlocked = OffsetDateTime.MIN;
  }
}
