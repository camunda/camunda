/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
public abstract class AllEntitiesBasedImportIndexHandler
  implements ImportIndexHandler<AllEntitiesBasedImportPage, AllEntitiesBasedImportIndexDto> {

  @Autowired
  protected ImportIndexReader importIndexReader;
  @Autowired
  protected ConfigurationService configurationService;

  private long importIndex = 0;
  protected EngineContext engineContext;

  public AllEntitiesBasedImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(EsHelper.constructKey(getElasticsearchImportIndexType(), engineContext.getEngineAlias()));
    if (storedIndex.isPresent()) {
      importIndex = storedIndex.get().getImportIndex();
    }
  }

  @Override
  public AllEntitiesBasedImportIndexDto createIndexInformationForStoring() {
    AllEntitiesBasedImportIndexDto indexToStore = new AllEntitiesBasedImportIndexDto();
    indexToStore.setImportIndex(importIndex);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchImportIndexType());
    indexToStore.setEngine(engineContext.getEngineAlias());
    return indexToStore;
  }

  @Override
  public AllEntitiesBasedImportPage getNextPage() {
    AllEntitiesBasedImportPage page = new AllEntitiesBasedImportPage();
    page.setIndexOfFirstResult(0);
    page.setPageSize(Integer.MAX_VALUE);
    return page;
  }

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
  public EngineContext getEngineContext() {
    return engineContext;
  }

  @Override
  public void executeAfterMaxBackoffIsReached() {
    resetImportIndex();
  }
}
