package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.util.BeanHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EngineImportIndexHandlerProvider {

  @Autowired
  private BeanHelper beanHelper;

  private final EngineContext engineContext;

  private List<AllEntitiesBasedImportIndexHandler>  allEntitiesBasedHandlers;
  private List<ScrollBasedImportIndexHandler>       scrollBasedHandlers;
  private List<DefinitionBasedImportIndexHandler>   definitionBasedHandlers;
  private Map<String, ImportIndexHandler>           allHandlers;

  public EngineImportIndexHandlerProvider(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    allEntitiesBasedHandlers = new ArrayList<>();
    scrollBasedHandlers = new ArrayList<>();
    definitionBasedHandlers = new ArrayList<>();
    allHandlers = new HashMap<>();

    allHandlers.put(ActivityImportIndexHandler.class.getSimpleName(), getActivityImportIndexHandler());
    allHandlers.put(FinishedProcessInstanceImportIndexHandler.class.getSimpleName(), getFinishedProcessInstanceImportIndexHandler());
    allHandlers.put(ProcessDefinitionImportIndexHandler.class.getSimpleName(), getProcessDefinitionImportIndexHandler());
    allHandlers.put(ProcessDefinitionXmlImportIndexHandler.class.getSimpleName(), getProcessDefinitionXmlImportIndexHandler());
    allHandlers.put(UnfinishedProcessInstanceImportIndexHandler.class.getSimpleName(), getUnfinishedProcessInstanceImportIndexHandler());
    allHandlers.put(VariableInstanceImportIndexHandler.class.getSimpleName(), getVariableInstanceImportIndexHandler());

    scrollBasedHandlers.add(getUnfinishedProcessInstanceImportIndexHandler());
    scrollBasedHandlers.add(getVariableInstanceImportIndexHandler());

    definitionBasedHandlers.add(getActivityImportIndexHandler());
    definitionBasedHandlers.add(getFinishedProcessInstanceImportIndexHandler());

    definitionBasedHandlers.add(getProcessDefinitionXmlImportIndexHandler());
    definitionBasedHandlers.add(getProcessDefinitionImportIndexHandler());
  }


  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers() {
    return allEntitiesBasedHandlers;
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedHandlers() {
    return definitionBasedHandlers;
  }

  public List<ScrollBasedImportIndexHandler> getScrollBasedHandlers() {
    return scrollBasedHandlers;
  }

  /**
   * Instantiate index handler for given engine if it has not been instantiated yet.
   * otherwise return already existing instance.
   *
   * @param engineContext - engine alias for instantiation
   * @param requiredType - type of index handler
   * @param <R> - Index handler instance
   * @param <C> - Class signature of required index handler
   * @return
   */
  protected <R, C extends Class<R>> R getImportIndexHandlerInstance(EngineContext engineContext, C requiredType) {
    R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(
          allHandlers.get(requiredType.getSimpleName())
      );
    } else {
      result = beanHelper.getInstance(requiredType, engineContext);
    }
    return result;
  }

  protected boolean isInstantiated(Class handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }

  public ActivityImportIndexHandler getActivityImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, ActivityImportIndexHandler.class);
  }

  public FinishedProcessInstanceImportIndexHandler getFinishedProcessInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, FinishedProcessInstanceImportIndexHandler.class);
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, ProcessDefinitionImportIndexHandler.class);
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, ProcessDefinitionXmlImportIndexHandler.class);
  }

  public UnfinishedProcessInstanceImportIndexHandler getUnfinishedProcessInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, UnfinishedProcessInstanceImportIndexHandler.class);
  }

  public VariableInstanceImportIndexHandler getVariableInstanceImportIndexHandler() {
    return getImportIndexHandlerInstance(engineContext, VariableInstanceImportIndexHandler.class);
  }

  public List<ImportIndexHandler> getAllHandlers() {
    return new ArrayList<>(allHandlers.values());
  }
}
