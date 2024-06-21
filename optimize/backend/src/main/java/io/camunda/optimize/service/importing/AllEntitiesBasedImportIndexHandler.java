/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import io.camunda.optimize.service.db.reader.ImportIndexReader;
import io.camunda.optimize.service.importing.page.AllEntitiesBasedImportPage;
import io.camunda.optimize.service.util.DatabaseHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AllEntitiesBasedImportIndexHandler
    implements EngineImportIndexHandler<
    AllEntitiesBasedImportPage, AllEntitiesBasedImportIndexDto> {

  @Autowired
  protected ImportIndexReader importIndexReader;

  @Autowired
  protected ConfigurationService configurationService;

  private long importIndex = 0;

  public void readIndexFromDatabase() {
    final Optional<AllEntitiesBasedImportIndexDto> storedIndex =
        importIndexReader.getImportIndex(
            DatabaseHelper.constructKey(getDatabaseImportIndexType(), getEngineAlias()));
    storedIndex.ifPresent(
        allEntitiesBasedImportIndexDto ->
            importIndex = allEntitiesBasedImportIndexDto.getImportIndex());
  }

  @Override
  public AllEntitiesBasedImportIndexDto getIndexStateDto() {
    final AllEntitiesBasedImportIndexDto indexToStore = new AllEntitiesBasedImportIndexDto();
    indexToStore.setImportIndex(importIndex);
    indexToStore.setEsTypeIndexRefersTo(getDatabaseImportIndexType());
    indexToStore.setEngine(getEngineAlias());
    return indexToStore;
  }

  @Override
  public void resetImportIndex() {
    importIndex = 0;
  }

  public Long getImportIndex() {
    return importIndex;
  }

  @PostConstruct
  protected void init() {
    readIndexFromDatabase();
  }

  protected abstract String getDatabaseImportIndexType();

  protected void moveImportIndex(final long units) {
    importIndex += units;
  }
}
