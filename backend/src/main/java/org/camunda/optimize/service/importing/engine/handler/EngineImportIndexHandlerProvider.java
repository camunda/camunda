/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.importing.EngineImportIndexHandler;
import org.camunda.optimize.service.importing.TimestampBasedEngineImportIndexHandler;
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
public class EngineImportIndexHandlerProvider {
  private static final List<Class<?>> TIMESTAMP_BASED_HANDLER_CLASSES;
  private static final List<Class<?>> SCROLL_BASED_HANDLER_CLASSES;
  private static final List<Class<?>> ALL_ENTITIES_HANDLER_CLASSES;

  static {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(EngineImportIndexHandlerProvider.class.getPackage().getName())
      .scan()) {
      TIMESTAMP_BASED_HANDLER_CLASSES = scanResult.getSubclasses(TimestampBasedEngineImportIndexHandler.class.getName())
        .loadClasses();
      SCROLL_BASED_HANDLER_CLASSES = scanResult.getSubclasses(DefinitionXmlImportIndexHandler.class.getName())
        .loadClasses();
      ALL_ENTITIES_HANDLER_CLASSES = scanResult.getSubclasses(AllEntitiesBasedImportIndexHandler.class.getName())
        .loadClasses();
    }
  }

  private final EngineContext engineContext;
  @Autowired
  private BeanFactory beanFactory;
  private List<AllEntitiesBasedImportIndexHandler> allEntitiesBasedHandlers;
  private List<DefinitionXmlImportIndexHandler> scrollBasedHandlers;
  private List<TimestampBasedEngineImportIndexHandler> timestampBasedEngineHandlers;
  private Map<String, EngineImportIndexHandler<?, ?>> allHandlers;

  public EngineImportIndexHandlerProvider(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    allHandlers = new HashMap<>();

    scrollBasedHandlers = new ArrayList<>();
    allEntitiesBasedHandlers = new ArrayList<>();
    timestampBasedEngineHandlers = new ArrayList<>();

    TIMESTAMP_BASED_HANDLER_CLASSES
      .forEach(clazz -> {
        final TimestampBasedEngineImportIndexHandler importIndexHandlerInstance =
          (TimestampBasedEngineImportIndexHandler) getImportIndexHandlerInstance(engineContext, clazz);
        timestampBasedEngineHandlers.add(importIndexHandlerInstance);
        allHandlers.put(clazz.getSimpleName(), importIndexHandlerInstance);
      });

    SCROLL_BASED_HANDLER_CLASSES
      .forEach(clazz -> {
        EngineImportIndexHandler<?, ?> engineImportIndexHandlerInstance =
          (EngineImportIndexHandler) getImportIndexHandlerInstance(
            engineContext,
            clazz
          );
        scrollBasedHandlers.add((DefinitionXmlImportIndexHandler) engineImportIndexHandlerInstance);
        allHandlers.put(clazz.getSimpleName(), engineImportIndexHandlerInstance);
      });

    ALL_ENTITIES_HANDLER_CLASSES
      .forEach(clazz -> {
        EngineImportIndexHandler<?, ?> engineImportIndexHandlerInstance =
          (EngineImportIndexHandler) getImportIndexHandlerInstance(
            engineContext,
            clazz
          );
        allEntitiesBasedHandlers.add((AllEntitiesBasedImportIndexHandler) engineImportIndexHandlerInstance);
        allHandlers.put(clazz.getSimpleName(), engineImportIndexHandlerInstance);
      });
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers() {
    return allEntitiesBasedHandlers;
  }

  public List<TimestampBasedEngineImportIndexHandler> getTimestampBasedEngineHandlers() {
    return timestampBasedEngineHandlers;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <C extends EngineImportIndexHandler<?, ?>> C getImportIndexHandler(Class<C> clazz) {
    return (C) allHandlers.get(clazz.getSimpleName());
  }

  public List<EngineImportIndexHandler<?, ?>> getAllHandlers() {
    return new ArrayList<>(allHandlers.values());
  }

  /**
   * Instantiate index handler for given engine if it has not been instantiated yet.
   * otherwise return already existing instance.
   *
   * @param engineContext - engine alias for instantiation
   * @param requiredType  - type of index handler
   * @param <R>           - Index handler instance
   * @param <C>           - Class signature of required index handler
   */
  private <R, C extends Class<R>> R getImportIndexHandlerInstance(EngineContext engineContext, C requiredType) {
    R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(
        allHandlers.get(requiredType.getSimpleName())
      );
    } else {
      result = beanFactory.getBean(requiredType, engineContext);
    }
    return result;
  }

  private boolean isInstantiated(Class<?> handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }
}
