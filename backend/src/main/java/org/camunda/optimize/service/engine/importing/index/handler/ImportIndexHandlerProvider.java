/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.index.handler;

import lombok.RequiredArgsConstructor;
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
import org.camunda.optimize.service.engine.importing.index.handler.impl.TenantImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UserOperationLogInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableUpdateInstanceImportIndexHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ImportIndexHandlerProvider {

  private final BeanFactory beanFactory;

  private Map<String, EngineImportIndexHandlerProvider> engineImportIndexHandlerProviderMap = new HashMap<>();

  /**
   * NOTE: this method has to be invoked at least once before providers for engine can be requested.
   *
   * @param engineContext
   */
  public void init(EngineContext engineContext) {
    engineImportIndexHandlerProviderMap.put(
      engineContext.getEngineAlias(),
      beanFactory.getBean(EngineImportIndexHandlerProvider.class, engineContext)
    );
  }


  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers(String engineAlias) {
    List<AllEntitiesBasedImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getAllEntitiesBasedHandlers();
    }
    return result;
  }

  public List<TimestampBasedImportIndexHandler> getDefinitionBasedHandlers(String engineAlias) {
    List<TimestampBasedImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getTimestampBasedHandlers();
    }
    return result;
  }

  public List<ScrollBasedImportIndexHandler> getAllScrollBasedHandlers(String engineAlias) {
    List<ScrollBasedImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getScrollBasedHandlers();
    }
    return result;
  }

  public CompletedProcessInstanceImportIndexHandler getCompletedProcessInstanceImportIndexHandler(String engineAlias) {
    CompletedProcessInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getCompletedProcessInstanceImportIndexHandler();
    }
    return result;
  }

  public CompletedActivityInstanceImportIndexHandler getCompletedActivityInstanceImportIndexHandler(String engineAlias) {
    CompletedActivityInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getCompletedActivityImportIndexHandler();
    }
    return result;
  }

  public RunningActivityInstanceImportIndexHandler getRunningActivityInstanceImportIndexHandler(String engineAlias) {
    RunningActivityInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getRunningActivityImportIndexHandler();
    }
    return result;
  }

  public RunningProcessInstanceImportIndexHandler getRunningProcessInstanceImportIndexHandler(String engineAlias) {
    RunningProcessInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getRunningProcessInstanceImportIndexHandler();
    }
    return result;
  }

  public VariableUpdateInstanceImportIndexHandler getRunningVariableInstanceImportIndexHandler(String engineAlias) {
    VariableUpdateInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getVariableUpdateInstanceImportIndexHandler();
    }
    return result;
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler(String engineAlias) {
    ProcessDefinitionImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getProcessDefinitionImportIndexHandler();
    }
    return result;
  }

  public CompletedUserTaskInstanceImportIndexHandler getCompletedUserTaskInstanceImportIndexHandler(String engineAlias) {
    CompletedUserTaskInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getCompletedUserTaskInstanceImportIndexHandler();
    }
    return result;
  }

  public UserOperationLogInstanceImportIndexHandler getUserOperationLogImportIndexHandler(String engineAlias) {
    UserOperationLogInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getUserOperationLogImportIndexHandler();
    }
    return result;
  }

  public List<ImportIndexHandler> getAllHandlers() {
    List<ImportIndexHandler> result = new ArrayList<>();
    for (EngineImportIndexHandlerProvider provider : engineImportIndexHandlerProviderMap.values()) {
      result.addAll(provider.getAllHandlers());
    }
    return result;
  }

  public List<ImportIndexHandler> getAllHandlers(String engine) {
    List<ImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engine);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getAllHandlers();
    }
    return result;
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler(String engineAlias) {
    ProcessDefinitionXmlImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getProcessDefinitionXmlImportIndexHandler();
    }
    return result;
  }

  public DecisionDefinitionImportIndexHandler getDecisionDefinitionImportIndexHandler(String engineAlias) {
    final EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    DecisionDefinitionImportIndexHandler result = null;
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getDecisionDefinitionImportIndexHandler();
    }
    return result;
  }

  public DecisionDefinitionXmlImportIndexHandler getDecisionDefinitionXmlImportIndexHandler(String engineAlias) {
    DecisionDefinitionXmlImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getDecisionDefinitionXmlImportIndexHandler();
    }
    return result;
  }

  public DecisionInstanceImportIndexHandler getDecisionInstanceImportIndexHandler(String engineAlias) {
    DecisionInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(
      engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getDecisionInstanceImportIndexHandler();
    }
    return result;
  }

  public TenantImportIndexHandler getTenantImportIndexHandler(String engineAlias) {
    final EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap
      .get(engineAlias);
    TenantImportIndexHandler result = null;
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getTenantImportIndexHandler();
    }
    return result;
  }

  public void reloadConfiguration() {
    this.engineImportIndexHandlerProviderMap = new HashMap<>();
  }
}
