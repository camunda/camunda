/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.handler;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import io.camunda.optimize.service.importing.ZeebeImportIndexHandler;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeImportIndexHandlerProvider {

  private static final List<Class<?>> POSITION_BASED_HANDLER_CLASSES;

  static {
    try (final ScanResult scanResult =
        new ClassGraph()
            .enableClassInfo()
            .acceptPackages(ZeebeImportIndexHandlerProvider.class.getPackage().getName())
            .scan()) {
      POSITION_BASED_HANDLER_CLASSES =
          scanResult.getSubclasses(PositionBasedImportIndexHandler.class.getName()).loadClasses();
    }
  }

  private final ZeebeDataSourceDto zeebeDataSourceDto;
  @Autowired private BeanFactory beanFactory;
  private Map<String, PositionBasedImportIndexHandler> positionBasedHandlersByName;

  public ZeebeImportIndexHandlerProvider(final ZeebeDataSourceDto zeebeDataSourceDto) {
    this.zeebeDataSourceDto = zeebeDataSourceDto;
  }

  @PostConstruct
  public void init() {
    positionBasedHandlersByName = new HashMap<>();

    POSITION_BASED_HANDLER_CLASSES.forEach(
        clazz -> {
          final PositionBasedImportIndexHandler importIndexHandlerInstance =
              (PositionBasedImportIndexHandler)
                  getImportIndexHandlerInstance(zeebeDataSourceDto, clazz);
          positionBasedHandlersByName.put(clazz.getSimpleName(), importIndexHandlerInstance);
        });
  }

  public List<PositionBasedImportIndexHandler> getPositionBasedImportHandlers() {
    return new ArrayList<>(positionBasedHandlersByName.values());
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <C extends ZeebeImportIndexHandler> C getImportIndexHandler(final Class<C> clazz) {
    return (C) positionBasedHandlersByName.get(clazz.getSimpleName());
  }

  private <R, C extends Class<R>> R getImportIndexHandlerInstance(
      final ZeebeDataSourceDto zeebeDataSourceDto, final C requiredType) {
    final R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(positionBasedHandlersByName.get(requiredType.getSimpleName()));
    } else {
      result = beanFactory.getBean(requiredType, zeebeDataSourceDto);
    }
    return result;
  }

  private boolean isInstantiated(final Class<?> handlerClass) {
    return positionBasedHandlersByName.get(handlerClass.getSimpleName()) != null;
  }
}
