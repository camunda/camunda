/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.service.importing.BackoffImportMediator;
import io.camunda.optimize.service.importing.engine.handler.DefinitionXmlImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public abstract class DefinitionXmlImportMediator<T extends DefinitionXmlImportIndexHandler, DTO>
    extends BackoffImportMediator<T, DTO> {

  protected DefinitionXmlImportMediator(
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator,
      final T importIndexHandler,
      final ImportService<DTO> importService) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
  }

  public void reset() {
    importIndexHandler.resetImportIndex();
  }

  protected abstract List<DTO> getEntities(IdSetBasedImportPage page);

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      List<DTO> entities = getEntities(page);
      if (!entities.isEmpty()) {
        importIndexHandler.updateIndex(page.getIds().size());
        importService.executeImport(
            filterEntitiesFromExcludedTenants(entities), importCompleteCallback);
      } else {
        importCompleteCallback.run();
      }
      return true;
    }
    importCompleteCallback.run();
    return false;
  }
}
