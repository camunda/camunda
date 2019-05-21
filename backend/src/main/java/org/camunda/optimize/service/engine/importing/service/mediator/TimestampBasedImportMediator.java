/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.service.ImportService;

import java.time.OffsetDateTime;
import java.util.List;

public abstract class TimestampBasedImportMediator<T extends TimestampBasedImportIndexHandler, DTO>
  extends BackoffImportMediator<T> {

  protected ImportService<DTO> importService;

  public TimestampBasedImportMediator(final EngineContext engineContext) {
    super(engineContext);
  }

  protected abstract OffsetDateTime getTimestamp(final DTO dto);

  protected abstract List<DTO> getEntitiesNextPage();

  protected abstract List<DTO> getEntitiesLastTimestamp();

  protected abstract int getMaxPageSize();

  @Override
  protected boolean importNextEnginePage() {
    return importNextEnginePageTimestampBased(getEntitiesLastTimestamp(), getEntitiesNextPage(), getMaxPageSize());
  }

  protected boolean importNextEnginePageTimestampBased(final List<DTO> entitiesLastTimestamp,
                                                       final List<DTO> entitiesNextPage,
                                                       int maxPageSize) {
    boolean timestampNeedsToBeSet = !entitiesNextPage.isEmpty();

    final OffsetDateTime timestamp = timestampNeedsToBeSet ?
      getTimestamp(entitiesNextPage.get(entitiesNextPage.size() - 1)) : null;

    final List<DTO> allEntities = ImmutableList.<DTO>builder()
      .addAll(entitiesLastTimestamp)
      .addAll(entitiesNextPage)
      .build();

    if (timestampNeedsToBeSet) {
      importIndexHandler.updatePendingTimestampOfLastEntity(timestamp);

      importService.executeImport(allEntities, () -> importIndexHandler.updateTimestampOfLastEntity(timestamp));
    } else if (!entitiesLastTimestamp.isEmpty()) {
      importService.executeImport(allEntities);
    }

    return entitiesNextPage.size() >= maxPageSize;
  }

}
