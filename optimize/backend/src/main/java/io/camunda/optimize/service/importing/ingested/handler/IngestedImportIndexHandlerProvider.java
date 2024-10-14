/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.handler;

import io.camunda.optimize.service.importing.ImportIndexHandler;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IngestedImportIndexHandlerProvider {

  private final BeanFactory beanFactory;

  private Map<String, ImportIndexHandler<?, ?>> allHandlers;
  private ExternalVariableUpdateImportIndexHandler externalVariableUpdateImportIndexHandler;

  public IngestedImportIndexHandlerProvider(final BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @PostConstruct
  public void init() {
    allHandlers = new HashMap<>();
    final ExternalVariableUpdateImportIndexHandler importIndexHandlerInstance =
        getImportIndexHandlerInstance(ExternalVariableUpdateImportIndexHandler.class);
    externalVariableUpdateImportIndexHandler = importIndexHandlerInstance;
    allHandlers.put(
        ExternalVariableUpdateImportIndexHandler.class.getSimpleName(), importIndexHandlerInstance);
  }

  public Collection<ImportIndexHandler<?, ?>> getAllHandlers() {
    return allHandlers.values();
  }

  private <R, C extends Class<R>> R getImportIndexHandlerInstance(final C requiredType) {
    final R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(allHandlers.get(requiredType.getSimpleName()));
    } else {
      result = beanFactory.getBean(requiredType);
    }
    return result;
  }

  private boolean isInstantiated(final Class<?> handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }

  public ExternalVariableUpdateImportIndexHandler getExternalVariableUpdateImportIndexHandler() {
    return externalVariableUpdateImportIndexHandler;
  }
}
