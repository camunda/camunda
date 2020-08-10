/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class TimestampBasedImportMediator<T extends TimestampBasedImportIndexHandler<?>, DTO>
  extends BackoffImportMediator<T, DTO> {

  protected int countOfImportedEntitiesWithLastEntityTimestamp = 0;

  protected abstract OffsetDateTime getTimestamp(final DTO dto);

  protected abstract List<DTO> getEntitiesNextPage();

  protected abstract List<DTO> getEntitiesLastTimestamp();

  protected abstract int getMaxPageSize();

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    return importNextEnginePageTimestampBased(
      getEntitiesLastTimestamp(), getEntitiesNextPage(), getMaxPageSize(), importCompleteCallback
    );
  }

  protected boolean importNextEnginePageTimestampBased(final List<DTO> entitiesLastTimestamp,
                                                       final List<DTO> entitiesNextPage,
                                                       final int maxPageSize,
                                                       final Runnable importCompleteCallback) {
    importIndexHandler.updateLastImportExecutionTimestamp();
    if (!entitiesNextPage.isEmpty()) {
      final List<DTO> allEntities = new ArrayList<>();
      if (entitiesLastTimestamp.size() > countOfImportedEntitiesWithLastEntityTimestamp) {
        allEntities.addAll(entitiesLastTimestamp);
      }
      allEntities.addAll(entitiesNextPage);

      final OffsetDateTime currentPageLastEntityTimestamp =
        getTimestamp(entitiesNextPage.get(entitiesNextPage.size() - 1));
      importService.executeImport(allEntities, () -> {
        importIndexHandler.updateTimestampOfLastEntity(currentPageLastEntityTimestamp);
        importCompleteCallback.run();
      });
      countOfImportedEntitiesWithLastEntityTimestamp = (int) entitiesNextPage
        .stream()
        .filter(entity -> getTimestamp(entity).equals(currentPageLastEntityTimestamp))
        .count();
      importIndexHandler.updatePendingTimestampOfLastEntity(currentPageLastEntityTimestamp);
    } else if (entitiesLastTimestamp.size() > countOfImportedEntitiesWithLastEntityTimestamp) {
      countOfImportedEntitiesWithLastEntityTimestamp = entitiesLastTimestamp.size();
      importService.executeImport(entitiesLastTimestamp, importCompleteCallback);
    } else {
      importCompleteCallback.run();
    }

    return entitiesNextPage.size() >= maxPageSize;
  }

}
