/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.importing.EngineImportIndexHandler;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StoreEngineImportProgressMediator
    extends AbstractStoreIndexesImportMediator<StoreIndexesEngineImportService>
    implements ImportMediator {

  protected EngineContext engineContext;
  private ImportIndexHandlerRegistry importIndexHandlerRegistry;

  public StoreEngineImportProgressMediator(
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final StoreIndexesEngineImportService importService,
      final EngineContext engineContext,
      final ConfigurationService configurationService) {
    super(importService, configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.engineContext = engineContext;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<EngineImportIndexDto> importIndexes =
          Stream.concat(
                  createStreamForHandlers(
                      importIndexHandlerRegistry.getAllEntitiesBasedHandlers(
                          engineContext.getEngineAlias())),
                  createStreamForHandlers(
                      importIndexHandlerRegistry.getTimestampEngineBasedHandlers(
                          engineContext.getEngineAlias())))
              .map(EngineImportIndexHandler::getIndexStateDto)
              .filter(Objects::nonNull)
              .map(EngineImportIndexDto.class::cast)
              .collect(Collectors.toList());

      importService.executeImport(importIndexes, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing engine index information!", e);
      importCompleted.complete(null);
    }
    return importCompleted;
  }

  private <T extends EngineImportIndexHandler> Stream<T> createStreamForHandlers(List<T> handlers) {
    return Optional.ofNullable(handlers).stream().flatMap(Collection::stream);
  }
}
