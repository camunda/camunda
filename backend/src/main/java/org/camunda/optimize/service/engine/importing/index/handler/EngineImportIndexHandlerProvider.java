/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.RunningActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UserOperationLogInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableUpdateInstanceImportIndexHandler;
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


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EngineImportIndexHandlerProvider {

  @Autowired
  private BeanFactory beanFactory;

  private final EngineContext engineContext;

  private List<AllEntitiesBasedImportIndexHandler> allEntitiesBasedHandlers;
  private List<ScrollBasedImportIndexHandler> scrollBasedHandlers;
  private List<TimestampBasedImportIndexHandler> timestampBasedHandlers;
  private Map<String, ImportIndexHandler> allHandlers;

  public EngineImportIndexHandlerProvider(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    allEntitiesBasedHandlers = new ArrayList<>();
    scrollBasedHandlers = new ArrayList<>();
    timestampBasedHandlers = new ArrayList<>();
    allHandlers = new HashMap<>();

    scrollBasedHandlers.add(getProcessDefinitionXmlImportIndexHandler());
    scrollBasedHandlers.add(getDecisionDefinitionXmlImportIndexHandler());

    timestampBasedHandlers.add(getRunningProcessInstanceImportIndexHandler());
    timestampBasedHandlers.add(getCompletedActivityImportIndexHandler());
    timestampBasedHandlers.add(getCompletedProcessInstanceImportIndexHandler());
    timestampBasedHandlers.add(getVariableUpdateInstanceImportIndexHandler());
    timestampBasedHandlers.add(getDecisionInstanceImportIndexHandler());
    timestampBasedHandlers.add(getRunningActivityImportIndexHandler());
    timestampBasedHandlers.add(getCompletedUserTaskInstanceImportIndexHandler());
    timestampBasedHandlers.add(getUserOperationLogImportIndexHandler());

    allEntitiesBasedHandlers.add(getProcessDefinitionImportIndexHandler());
    allEntitiesBasedHandlers.add(getDecisionDefinitionImportIndexHandler());

    scrollBasedHandlers.forEach(scrollBasedHandler -> allHandlers.put(
      scrollBasedHandler.getClass().getSimpleName(), scrollBasedHandler
    ));
    timestampBasedHandlers.forEach(timestampBasedHandler -> allHandlers.put(
      timestampBasedHandler.getClass().getSimpleName(), timestampBasedHandler
    ));
    allEntitiesBasedHandlers.forEach(allEntititesBasedHandler -> allHandlers.put(
      allEntititesBasedHandler.getClass().getSimpleName(), allEntititesBasedHandler
    ));
  }


  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers() {
    return allEntitiesBasedHandlers;
  }

  public List<TimestampBasedImportIndexHandler> getTimestampBasedHandlers() {
    return timestampBasedHandlers;
  }

  public List<ScrollBasedImportIndexHandler> getScrollBasedHandlers() {
    return scrollBasedHandlers;
  }

  /**
   * Instantiate index handler for given engine if it has not been instantiated yet.
   * otherwise return already existing instance.
   *
   * @param engineContext - engine alias for instantiation
   * @param requiredType  - type of index handler
   * @param <R>           - Index handler instance
   * @param <C>           - Class signature of required index handler
   * @return
   */
  protected <R, C extends Class<R>> R getImportIndexHandlerInstance(EngineContext engineContext, C requiredType) {
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

  protected boolean isInstantiated(Class handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }

  public CompletedActivityInstanceImportIndexHandler getCompletedActivityImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, CompletedActivityInstanceImportIndexHandler.class);
  }

  public RunningActivityInstanceImportIndexHandler getRunningActivityImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, RunningActivityInstanceImportIndexHandler.class);
  }

  public CompletedProcessInstanceImportIndexHandler getCompletedProcessInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, CompletedProcessInstanceImportIndexHandler.class);
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, ProcessDefinitionImportIndexHandler.class);
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, ProcessDefinitionXmlImportIndexHandler.class);
  }

  public RunningProcessInstanceImportIndexHandler getRunningProcessInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, RunningProcessInstanceImportIndexHandler.class);
  }

  public VariableUpdateInstanceImportIndexHandler getVariableUpdateInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, VariableUpdateInstanceImportIndexHandler.class);
  }

  public CompletedUserTaskInstanceImportIndexHandler getCompletedUserTaskInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, CompletedUserTaskInstanceImportIndexHandler.class);
  }

  public UserOperationLogInstanceImportIndexHandler getUserOperationLogImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, UserOperationLogInstanceImportIndexHandler.class);
  }

  public DecisionDefinitionImportIndexHandler getDecisionDefinitionImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, DecisionDefinitionImportIndexHandler.class);
  }

  public DecisionDefinitionXmlImportIndexHandler getDecisionDefinitionXmlImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, DecisionDefinitionXmlImportIndexHandler.class);
  }

  public DecisionInstanceImportIndexHandler getDecisionInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, DecisionInstanceImportIndexHandler.class);
  }

  public List<ImportIndexHandler> getAllHandlers() {
    return new ArrayList<>(allHandlers.values());
  }
}
