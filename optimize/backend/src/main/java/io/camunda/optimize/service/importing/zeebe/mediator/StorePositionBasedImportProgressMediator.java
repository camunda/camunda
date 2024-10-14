/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.ZeebeImportIndexHandler;
import io.camunda.optimize.service.importing.engine.mediator.AbstractStoreIndexesImportMediator;
import io.camunda.optimize.service.importing.engine.service.StorePositionBasedIndexImportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StorePositionBasedImportProgressMediator
    extends AbstractStoreIndexesImportMediator<StorePositionBasedIndexImportService>
    implements ImportMediator {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(StorePositionBasedImportProgressMediator.class);
  private final ImportIndexHandlerRegistry importIndexHandlerRegistry;
  private final ZeebeDataSourceDto dataSource;

  public StorePositionBasedImportProgressMediator(
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final StorePositionBasedIndexImportService importService,
      final ConfigurationService configurationService,
      final ZeebeDataSourceDto zeebeDataSourceDto) {
    super(importService, configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    dataSource = zeebeDataSourceDto;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<PositionBasedImportIndexDto> importIndexes =
          Optional.ofNullable(
                  importIndexHandlerRegistry.getPositionBasedHandlers(dataSource.getPartitionId()))
              .stream()
              .flatMap(Collection::stream)
              .map(ZeebeImportIndexHandler::getIndexStateDto)
              .filter(Objects::nonNull)
              .map(PositionBasedImportIndexDto.class::cast)
              .collect(Collectors.toList());
      importService.executeImport(importIndexes, () -> importCompleted.complete(null));
    } catch (final Exception e) {
      log.error("Could not execute import for storing zeebe position based index information!", e);
      importCompleted.complete(null);
    }
    return importCompleted;
  }
}
