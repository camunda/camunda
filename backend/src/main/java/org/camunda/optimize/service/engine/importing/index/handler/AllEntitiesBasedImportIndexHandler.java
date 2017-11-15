package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.OptionalDouble;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AllEntitiesBasedImportIndexHandler
  extends BackoffImportIndexHandler<AllEntitiesBasedImportPage, AllEntitiesBasedImportIndexDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected ImportIndexReader importIndexReader;

  @Autowired
  protected ConfigurationService configurationService;

  protected long importIndex = 0;
  protected long maxEntityCount = 0;

  @PostConstruct
  public void init() {
    readIndexFromElasticsearch();
    updateMaxEntityCount();
  }

  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(getElasticsearchImportIndexType());
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

  private void updateMaxEntityCount() {
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

}
