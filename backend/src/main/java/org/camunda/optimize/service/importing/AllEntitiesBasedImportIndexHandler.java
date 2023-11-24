/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.db.reader.ImportIndexReader;
import org.camunda.optimize.service.importing.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AllEntitiesBasedImportIndexHandler
  implements EngineImportIndexHandler<AllEntitiesBasedImportPage, AllEntitiesBasedImportIndexDto> {

  @Autowired
  protected ImportIndexReader importIndexReader;

  @Autowired
  protected ConfigurationService configurationService;

  private long importIndex = 0;

  public void readIndexFromDatabase() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(DatabaseHelper.constructKey(getDatabaseImportIndexType(), getEngineAlias()));
    storedIndex.ifPresent(
      allEntitiesBasedImportIndexDto -> importIndex =
        allEntitiesBasedImportIndexDto.getImportIndex()
    );
  }

  @Override
  public AllEntitiesBasedImportIndexDto getIndexStateDto() {
    AllEntitiesBasedImportIndexDto indexToStore = new AllEntitiesBasedImportIndexDto();
    indexToStore.setImportIndex(importIndex);
    indexToStore.setEsTypeIndexRefersTo(getDatabaseImportIndexType());
    indexToStore.setEngine(getEngineAlias());
    return indexToStore;
  }

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

  protected void moveImportIndex(long units) {
    importIndex += units;
  }

}
