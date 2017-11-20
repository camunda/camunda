package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.util.EngineInstanceHelper;
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
  private EngineInstanceHelper engineInstanceHelper;

  @Autowired
  private ConfigurationService configurationService;

  private Map<String, List<AllEntitiesBasedImportIndexHandler>> allEntitiesBasedHandlersMap;
  private Map<String, List<ScrollBasedImportIndexHandler>> scrollBasedHandlersMap;
  private Map<String, List<DefinitionBasedImportIndexHandler>> definitionBasedHandlersMap;
  private Map<String, Map<String, ImportIndexHandler>> allHandlersMap;


  @PostConstruct
  public void init() {
    allEntitiesBasedHandlersMap = new HashMap<>();
    scrollBasedHandlersMap = new HashMap<>();
    definitionBasedHandlersMap = new HashMap<>();
    allHandlersMap = new HashMap<>();

    for (String engineAlias : configurationService.getConfiguredEngines().keySet()) {
      Map<String, ImportIndexHandler> allHandlers = new HashMap<>();
      allHandlers.put(ActivityImportIndexHandler.class.getSimpleName(), getActivityImportIndexHandler(engineAlias));
      allHandlers.put(FinishedProcessInstanceImportIndexHandler.class.getSimpleName(), getFinishedProcessInstanceImportIndexHandler(engineAlias));
      allHandlers.put(ProcessDefinitionImportIndexHandler.class.getSimpleName(), getProcessDefinitionImportIndexHandler(engineAlias));
      allHandlers.put(ProcessDefinitionXmlImportIndexHandler.class.getSimpleName(), getProcessDefinitionXmlImportIndexHandler(engineAlias));
      allHandlers.put(UnfinishedProcessInstanceImportIndexHandler.class.getSimpleName(), getUnfinishedProcessInstanceImportIndexHandler(engineAlias));
      allHandlers.put(VariableInstanceImportIndexHandler.class.getSimpleName(), getVariableInstanceImportIndexHandler(engineAlias));

      allHandlersMap.put(engineAlias, allHandlers);

      List<AllEntitiesBasedImportIndexHandler> allEntitiesBasedHandlers = new ArrayList<>();
      allEntitiesBasedHandlers.add(getProcessDefinitionXmlImportIndexHandler(engineAlias));
      allEntitiesBasedHandlers.add(getProcessDefinitionImportIndexHandler(engineAlias));

      allEntitiesBasedHandlersMap.put(engineAlias, allEntitiesBasedHandlers);

      List<ScrollBasedImportIndexHandler> scrollBasedHandlers= new ArrayList<>();
      scrollBasedHandlers.add(getUnfinishedProcessInstanceImportIndexHandler(engineAlias));
      scrollBasedHandlers.add(getVariableInstanceImportIndexHandler(engineAlias));

      scrollBasedHandlersMap.put(engineAlias, scrollBasedHandlers);

      List<DefinitionBasedImportIndexHandler> definitionBasedHandlers = new ArrayList<>();
      definitionBasedHandlers.add(getActivityImportIndexHandler(engineAlias));
      definitionBasedHandlers.add(getFinishedProcessInstanceImportIndexHandler(engineAlias));

      definitionBasedHandlersMap.put(engineAlias, definitionBasedHandlers);
    }

  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers(String engineAlias) {
    return allEntitiesBasedHandlersMap.get(engineAlias);
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedHandlers(String engineAlias) {
    return definitionBasedHandlersMap.get(engineAlias);
  }

  protected <R, C extends Class<R>> R getImportIndexHandler (String engineAlias, C requiredType) {
    R result;
    if (isInstantiated(engineAlias, requiredType)) {
      result = requiredType.cast(
          allHandlersMap.get(engineAlias).get(requiredType.getSimpleName())
      );
    } else {
      result = engineInstanceHelper.getInstance(requiredType, engineAlias);
    }
    return result;
  }

  protected boolean isInstantiated(String engineAlias, Class handlerClass) {
    return allHandlersMap.get(engineAlias) != null && allHandlersMap.get(engineAlias).get(handlerClass.getSimpleName()) != null;
  }

  public ActivityImportIndexHandler getActivityImportIndexHandler(String engineAlias) {
    return getImportIndexHandler(engineAlias, ActivityImportIndexHandler.class);
  }

  public FinishedProcessInstanceImportIndexHandler getFinishedProcessInstanceImportIndexHandler(String engineAlias) {
    return (FinishedProcessInstanceImportIndexHandler) getImportIndexHandler(engineAlias, FinishedProcessInstanceImportIndexHandler.class);
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler(String engineAlias) {
    return (ProcessDefinitionImportIndexHandler) getImportIndexHandler(engineAlias, ProcessDefinitionImportIndexHandler.class);
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler(String engineAlias) {
    return (ProcessDefinitionXmlImportIndexHandler) getImportIndexHandler(engineAlias, ProcessDefinitionXmlImportIndexHandler.class);
  }

  public UnfinishedProcessInstanceImportIndexHandler getUnfinishedProcessInstanceImportIndexHandler(String engineAlias) {
    return (UnfinishedProcessInstanceImportIndexHandler) getImportIndexHandler(engineAlias, UnfinishedProcessInstanceImportIndexHandler.class);
  }

  public VariableInstanceImportIndexHandler getVariableInstanceImportIndexHandler(String engineAlias) {
    return (VariableInstanceImportIndexHandler) getImportIndexHandler(engineAlias, VariableInstanceImportIndexHandler.class);
  }

  public List<ImportIndexHandler> getAllHandlers() {
    List<ImportIndexHandler> result = new ArrayList<>();
    for (Map <String, ImportIndexHandler> handlerMap : allHandlersMap.values()) {
      result.addAll(handlerMap.values());
    }
    return result;
  }
}
