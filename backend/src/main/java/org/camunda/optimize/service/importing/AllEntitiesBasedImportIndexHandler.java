/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.importing.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import java.util.Optional;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AllEntitiesBasedImportIndexHandler
  implements ImportIndexHandler<AllEntitiesBasedImportPage, AllEntitiesBasedImportIndexDto> {

  @Autowired
  protected ImportIndexReader importIndexReader;
  @Autowired
  protected ConfigurationService configurationService;

  private long importIndex = 0;

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(EsHelper.constructKey(
        getElasticsearchImportIndexType(),
        getEngineAlias()
      ));
    if (storedIndex.isPresent()) {
      importIndex = storedIndex.get().getImportIndex();
    }
  }

  @Override
  public AllEntitiesBasedImportIndexDto createIndexInformationForStoring() {
    AllEntitiesBasedImportIndexDto indexToStore = new AllEntitiesBasedImportIndexDto();
    indexToStore.setImportIndex(importIndex);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchImportIndexType());
    indexToStore.setEngine(getEngineAlias());
    return indexToStore;
  }

  @Override
  public AllEntitiesBasedImportPage getNextPage() {
    AllEntitiesBasedImportPage page = new AllEntitiesBasedImportPage();
    page.setIndexOfFirstResult(0);
    page.setPageSize(getMaxPageSize());
    return page;
  }

  protected abstract int getMaxPageSize();

  protected abstract String getElasticsearchImportIndexType();

  public Long getImportIndex() {
    return importIndex;
  }

  public void moveImportIndex(long units) {
    importIndex += units;
  }

  public void resetImportIndex() {
    importIndex = 0;
  }

  @Override
  public void executeAfterMaxBackoffIsReached() {
    resetImportIndex();
  }
}
