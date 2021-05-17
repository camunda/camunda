/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe.handler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.ZeebeImportIndexHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeImportIndexHandlerProvider {

  private static final List<Class<?>> POSITION_BASED_HANDLER_CLASSES;

  static {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(ZeebeImportIndexHandlerProvider.class.getPackage().getName())
      .scan()) {
      POSITION_BASED_HANDLER_CLASSES = scanResult.getSubclasses(PositionBasedImportIndexHandler.class.getName())
        .loadClasses();
    }
  }

  private int partitionId;
  @Autowired
  private BeanFactory beanFactory;
  private List<PositionBasedImportIndexHandler> positionBasedImportIndexHandlers;
  private Map<String, ImportIndexHandler<?, ?>> allHandlers;

  public ZeebeImportIndexHandlerProvider(final int partitionId) {
    this.partitionId = partitionId;
  }

  @PostConstruct
  public void init() {
    positionBasedImportIndexHandlers = new ArrayList<>();
    allHandlers = new HashMap<>();

    POSITION_BASED_HANDLER_CLASSES
      .forEach(clazz -> {
        final PositionBasedImportIndexHandler importIndexHandlerInstance =
          (PositionBasedImportIndexHandler) getImportIndexHandlerInstance(partitionId, clazz);
        positionBasedImportIndexHandlers.add(importIndexHandlerInstance);
        allHandlers.put(clazz.getSimpleName(), importIndexHandlerInstance);
      });
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <C extends ZeebeImportIndexHandler> C getImportIndexHandler(Class<C> clazz) {
    return (C) allHandlers.get(clazz.getSimpleName());
  }

  private <R, C extends Class<R>> R getImportIndexHandlerInstance(int partitionId,
                                                                  C requiredType) {
    R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(
        allHandlers.get(requiredType.getSimpleName())
      );
    } else {
      result = beanFactory.getBean(requiredType, partitionId);
    }
    return result;
  }

  private boolean isInstantiated(Class<?> handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }
}
