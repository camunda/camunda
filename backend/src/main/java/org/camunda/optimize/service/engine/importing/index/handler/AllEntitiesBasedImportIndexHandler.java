package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalDouble;

@Component
public abstract class AllEntitiesBasedImportIndexHandler
  extends BackoffImportIndexHandler<AllEntitiesBasedImportPage, AllEntitiesBasedImportIndexDto> {

  @Autowired
  protected ImportIndexReader importIndexReader;

  protected long importIndex = 0;
  protected long maxEntityCount = 0;
  protected EngineContext engineContext;

  public AllEntitiesBasedImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  protected void init() {
    updateMaxEntityCount();
    readIndexFromElasticsearch();
  }

  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(EsHelper.constructKey(getElasticsearchImportIndexType(), engineContext.getEngineAlias()));
    if (storedIndex.isPresent()) {
      importIndex = storedIndex.get().getImportIndex();
      maxEntityCount = storedIndex.get().getMaxEntityCount();
    }
  }

  @Override
  public AllEntitiesBasedImportIndexDto createIndexInformationForStoring() {
    AllEntitiesBasedImportIndexDto indexToStore = new AllEntitiesBasedImportIndexDto();
    indexToStore.setImportIndex(importIndex);
    indexToStore.setMaxEntityCount(maxEntityCount);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchImportIndexType());
    indexToStore.setEngine(engineContext.getEngineAlias());
    return indexToStore;
  }

  @Override
  public Optional<AllEntitiesBasedImportPage> getNextImportPage() {
    if (canCreateNewPage()) {
      AllEntitiesBasedImportPage page = new AllEntitiesBasedImportPage();
      page.setIndexOfFirstResult(importIndex);
      long nextPageSize = getNextPageSize();
      page.setPageSize(nextPageSize);
      moveImportIndex(nextPageSize);
      return Optional.of(page);
    } else {
      return Optional.empty();
    }
  }

  public void updateMaxEntityCount() {
    this.maxEntityCount = fetchMaxEntityCount();
  }

  protected abstract long fetchMaxEntityCount();

  protected abstract long getMaxPageSize();

  protected abstract String getElasticsearchImportIndexType();

  public OptionalDouble computeProgress() {
    if (hasNothingToImport()) {
      return OptionalDouble.empty();
    } else if (indexReachedMaxCount()) {
      return OptionalDouble.of(100.0);
    } else {
      long maxCount = Math.max(1, maxEntityCount);
      return OptionalDouble.of(importIndex / maxCount * 100.0);
    }
  }

  private boolean hasNothingToImport() {
    return importIndex == 0 && maxEntityCount == 0;
  }

  protected boolean canCreateNewPage() {
    if (indexReachedMaxCount()) {
      updateMaxEntityCount();
      return !indexReachedMaxCount();
    }
    return true;
  }

  private boolean indexReachedMaxCount() {
    return importIndex >= maxEntityCount;
  }

  protected long getNextPageSize() {
    long diff = maxEntityCount - importIndex;
    long nextPageSize = Math.min(getMaxPageSize(), diff);
    nextPageSize = Math.max(0L, nextPageSize);
    return nextPageSize;
  }

  public Long getImportIndex() {
    return importIndex;
  }

  protected void moveImportIndex(long units) {
    importIndex += units;
  }

  public void resetImportIndex() {
    super.resetImportIndex();
    importIndex = 0;
  }

  @Override
  public EngineContext getEngineContext() {
    return engineContext;
  }
}
