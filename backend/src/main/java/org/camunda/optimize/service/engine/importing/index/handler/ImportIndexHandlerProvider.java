package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImportIndexHandlerProvider {
  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private BeanHelper beanHelper;

  @Autowired
  private EngineClientFactory engineClientFactory;

  private Map <String, EngineImportIndexHandlerProvider> engineImportIndexHandlerProviderMap = new HashMap<>();

  /**
   * NOTE: this method has to be invoked at least once before providers for engine can be requested.
   * @param engineAlias
   */
  public void init(String engineAlias) {
    engineImportIndexHandlerProviderMap.put(
        engineAlias,
        beanHelper.getInstance(EngineImportIndexHandlerProvider.class, engineAlias)
    );
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers(String engineAlias) {
    List<AllEntitiesBasedImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getAllEntitiesBasedHandlers();
    }
    return result;
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedHandlers(String engineAlias) {
    List<DefinitionBasedImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getDefinitionBasedHandlers();
    }
    return result;
  }

  public List<ScrollBasedImportIndexHandler> getAllScrollBasedHandlers(String engineAlias) {
    List<ScrollBasedImportIndexHandler> result = new ArrayList<>();
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getScrollBasedHandlers();
    }
    return result;
  }

  public FinishedProcessInstanceImportIndexHandler getFinishedProcessInstanceImportIndexHandler(String engineAlias) {
    FinishedProcessInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getFinishedProcessInstanceImportIndexHandler();
    }
    return result;
  }

  public ActivityImportIndexHandler getActivityImportIndexHandler(String engineAlias) {
    ActivityImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getActivityImportIndexHandler();
    }
    return result;
  }

  public UnfinishedProcessInstanceImportIndexHandler getUnfinishedProcessInstanceImportIndexHandler(String engineAlias) {
    UnfinishedProcessInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getUnfinishedProcessInstanceImportIndexHandler();
    }
    return result;
  }

  public VariableInstanceImportIndexHandler getVariableInstanceImportIndexHandler(String engineAlias) {
    VariableInstanceImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getVariableInstanceImportIndexHandler();
    }
    return result;
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler(String engineAlias) {
    ProcessDefinitionImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getProcessDefinitionImportIndexHandler();
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

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler(String engineAlias) {
    ProcessDefinitionXmlImportIndexHandler result = null;
    EngineImportIndexHandlerProvider engineImportIndexHandlerProvider = engineImportIndexHandlerProviderMap.get(engineAlias);
    if (engineImportIndexHandlerProvider != null) {
      result = engineImportIndexHandlerProvider.getProcessDefinitionXmlImportIndexHandler();
    }
    return result;
  }

  public void reloadConfiguration() {
    engineClientFactory.reloadConfiguration();
    this.engineImportIndexHandlerProviderMap = new HashMap<>();
  }
}
