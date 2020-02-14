/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;

import java.util.List;

public abstract class ScrollBasedImportMediator<T extends ScrollBasedImportIndexHandler, DTO>
  extends BackoffImportMediator<T> {

  protected ImportService<DTO> importService;
  
  protected abstract List<DTO> getEntities(IdSetBasedImportPage page);

  @Override
  protected boolean importNextPage() {
    IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      List<DTO> entities = getEntities(page);
      if (!entities.isEmpty()) {
        importIndexHandler.updateIndex(page.getIds().size());
        importService.executeImport(entities);
      }
      return true;
    }
    return false;
  }

  public void reset() {
    importIndexHandler.resetImportIndex();
  }

}
