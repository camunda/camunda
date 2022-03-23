/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.ZeebeImportIndexHandler;
import org.camunda.optimize.service.importing.engine.mediator.AbstractStoreIndexesImportMediator;
import org.camunda.optimize.service.importing.engine.service.StorePositionBasedIndexImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StorePositionBasedImportProgressMediator
  extends AbstractStoreIndexesImportMediator<StorePositionBasedIndexImportService> implements ImportMediator {

  private ImportIndexHandlerRegistry importIndexHandlerRegistry;
  private ZeebeDataSourceDto dataSource;

  public StorePositionBasedImportProgressMediator(final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                  final StorePositionBasedIndexImportService importService,
                                                  final ConfigurationService configurationService,
                                                  final ZeebeDataSourceDto zeebeDataSourceDto) {
    super(importService, configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.dataSource = zeebeDataSourceDto;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<PositionBasedImportIndexDto> importIndexes =
        Optional.ofNullable(importIndexHandlerRegistry.getPositionBasedHandlers(dataSource.getPartitionId())).stream()
          .flatMap(Collection::stream)
          .map(ZeebeImportIndexHandler::getIndexStateDto)
          .filter(Objects::nonNull)
          .map(PositionBasedImportIndexDto.class::cast)
          .collect(Collectors.toList());
      importService.executeImport(importIndexes, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing zeebe position based index information!", e);
      importCompleted.complete(null);
    }
    return importCompleted;
  }

}
