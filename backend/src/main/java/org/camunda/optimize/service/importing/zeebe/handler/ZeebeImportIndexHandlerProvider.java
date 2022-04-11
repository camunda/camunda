/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.handler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
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

  private ZeebeDataSourceDto zeebeDataSourceDto;
  @Autowired
  private BeanFactory beanFactory;
  private Map<String, PositionBasedImportIndexHandler> positionBasedHandlersByName;

  public ZeebeImportIndexHandlerProvider(final ZeebeDataSourceDto zeebeDataSourceDto) {
    this.zeebeDataSourceDto = zeebeDataSourceDto;
  }

  @PostConstruct
  public void init() {
    positionBasedHandlersByName = new HashMap<>();

    POSITION_BASED_HANDLER_CLASSES
      .forEach(clazz -> {
        final PositionBasedImportIndexHandler importIndexHandlerInstance =
          (PositionBasedImportIndexHandler) getImportIndexHandlerInstance(zeebeDataSourceDto, clazz);
        positionBasedHandlersByName.put(clazz.getSimpleName(), importIndexHandlerInstance);
      });
  }

  public List<PositionBasedImportIndexHandler> getPositionBasedEngineHandlers() {
    return new ArrayList<>(positionBasedHandlersByName.values());
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <C extends ZeebeImportIndexHandler> C getImportIndexHandler(Class<C> clazz) {
    return (C) positionBasedHandlersByName.get(clazz.getSimpleName());
  }

  private <R, C extends Class<R>> R getImportIndexHandlerInstance(ZeebeDataSourceDto zeebeDataSourceDto,
                                                                  C requiredType) {
    R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(
        positionBasedHandlersByName.get(requiredType.getSimpleName())
      );
    } else {
      result = beanFactory.getBean(requiredType, zeebeDataSourceDto);
    }
    return result;
  }

  private boolean isInstantiated(Class<?> handlerClass) {
    return positionBasedHandlersByName.get(handlerClass.getSimpleName()) != null;
  }
}
