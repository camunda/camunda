package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImportIndexHandlerProvider {

  @Autowired
  private BeanHelper beanHelper;

  @Autowired
  private ConfigurationService configurationService;

  private Map <String, EngineImportIndexHandlerProvider> engineImportIndexHandlerProviderMap;

  @PostConstruct
  public void init() {
    engineImportIndexHandlerProviderMap = new HashMap<>();
    for (String engineAlias : configurationService.getConfiguredEngines().keySet()) {
      engineImportIndexHandlerProviderMap.put(
          engineAlias,
          beanHelper.getInstance(EngineImportIndexHandlerProvider.class, engineAlias)
      );
    }
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getAllEntitiesBasedHandlers();
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedHandlers(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getDefinitionBasedHandlers();
  }

  public FinishedProcessInstanceImportIndexHandler getFinishedProcessInstanceImportIndexHandler(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getFinishedProcessInstanceImportIndexHandler();
  }

  public ActivityImportIndexHandler getActivityImportIndexHandler(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getActivityImportIndexHandler();
  }

  public UnfinishedProcessInstanceImportIndexHandler getUnfinishedProcessInstanceImportIndexHandler(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getUnfinishedProcessInstanceImportIndexHandler();
  }

  public VariableInstanceImportIndexHandler getVariableInstanceImportIndexHandler(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getVariableInstanceImportIndexHandler();
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getProcessDefinitionImportIndexHandler();
  }

  public List<ImportIndexHandler> getAllHandlers() {
    List<ImportIndexHandler> result = new ArrayList<>();
    for (EngineImportIndexHandlerProvider provider : engineImportIndexHandlerProviderMap.values()) {
      result.addAll(provider.getAllHandlers());
    }
    return result;
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler(String engineAlias) {
    return engineImportIndexHandlerProviderMap.get(engineAlias).getProcessDefinitionXmlImportIndexHandler();
  }
}
