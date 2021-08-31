/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested.handler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.TimestampBasedIngestedDataImportIndexHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IngestedImportIndexHandlerProvider {

  private static final List<Class<?>> TIMESTAMP_BASED_HANDLER_CLASSES;

  static {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(IngestedImportIndexHandlerProvider.class.getPackage().getName())
      .scan()) {
      TIMESTAMP_BASED_HANDLER_CLASSES = scanResult.getSubclasses(TimestampBasedIngestedDataImportIndexHandler.class.getName())
        .loadClasses();
    }
  }

  private final BeanFactory beanFactory;

  private Map<String, ImportIndexHandler<?, ?>> allHandlers;
  private List<TimestampBasedIngestedDataImportIndexHandler> timestampBasedIngestedDataHandlers;

  @PostConstruct
  public void init() {
    allHandlers = new HashMap<>();
    timestampBasedIngestedDataHandlers = new ArrayList<>();

    TIMESTAMP_BASED_HANDLER_CLASSES
      .forEach(clazz -> {
        final TimestampBasedIngestedDataImportIndexHandler importIndexHandlerInstance =
          (TimestampBasedIngestedDataImportIndexHandler) getImportIndexHandlerInstance(clazz);
        timestampBasedIngestedDataHandlers.add(importIndexHandlerInstance);
        allHandlers.put(clazz.getSimpleName(), importIndexHandlerInstance);
      });
  }

  public List<TimestampBasedIngestedDataImportIndexHandler> getTimestampBasedIngestedDataHandlers() {
    return timestampBasedIngestedDataHandlers;
  }

  public <C extends ImportIndexHandler<?, ?>> C getImportIndexHandler(Class<C> clazz) {
    return (C) allHandlers.get(clazz.getSimpleName());
  }

  public Collection<ImportIndexHandler<?, ?>> getAllHandlers() {
    return allHandlers.values();
  }

  private <R, C extends Class<R>> R getImportIndexHandlerInstance(C requiredType) {
    R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(
        allHandlers.get(requiredType.getSimpleName())
      );
    } else {
      result = beanFactory.getBean(requiredType);
    }
    return result;
  }

  private boolean isInstantiated(Class<?> handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }
}
