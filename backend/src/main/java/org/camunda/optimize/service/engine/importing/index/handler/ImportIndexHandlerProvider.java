package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImportIndexHandlerProvider {

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ActivityImportIndexHandler activityImportIndexHandler;
  @Autowired
  private FinishedProcessInstanceImportIndexHandler finishedProcessInstanceImportIndexHandler;
  @Autowired
  private ProcessDefinitionImportIndexHandler processDefinitionImportIndexHandler;
  @Autowired
  private ProcessDefinitionXmlImportIndexHandler processDefinitionXmlImportIndexHandler;
  @Autowired
  private UnfinishedProcessInstanceImportIndexHandler unfinishedProcessInstanceImportIndexHandler;
  @Autowired
  private VariableInstanceImportIndexHandler variableInstanceImportIndexHandler;

  private List<AllEntitiesBasedImportIndexHandler> allEntitiesBasedHandlers;
  private List<ScrollBasedImportIndexHandler> scrollBasedHandlers;
  private List<DefinitionBasedImportIndexHandler> definitionBasedHandlers;
  private List<ImportIndexHandler> allHandlers;


  @PostConstruct
  public void init() {

    allEntitiesBasedHandlers = new ArrayList<>();
    allEntitiesBasedHandlers.add(processDefinitionXmlImportIndexHandler);
    allEntitiesBasedHandlers.add(processDefinitionImportIndexHandler);

    scrollBasedHandlers= new ArrayList<>();
    scrollBasedHandlers.add(unfinishedProcessInstanceImportIndexHandler);
    scrollBasedHandlers.add(variableInstanceImportIndexHandler);

    definitionBasedHandlers = new ArrayList<>();
    definitionBasedHandlers.add(activityImportIndexHandler);
    definitionBasedHandlers.add(finishedProcessInstanceImportIndexHandler);

    allHandlers = new ArrayList<>();
    allHandlers.add(activityImportIndexHandler);
    allHandlers.add(finishedProcessInstanceImportIndexHandler);
    allHandlers.add(processDefinitionImportIndexHandler);
    allHandlers.add(processDefinitionXmlImportIndexHandler);
    allHandlers.add(unfinishedProcessInstanceImportIndexHandler);
    allHandlers.add(variableInstanceImportIndexHandler);
  }

  public List<ScrollBasedImportIndexHandler> getScrollBasedHandlers() {
    return scrollBasedHandlers;
  }

  public List<ImportIndexHandler> getAllHandlers() {
    return allHandlers;
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers() {
    return allEntitiesBasedHandlers;
  }

  public List<DefinitionBasedImportIndexHandler> getDefinitionBasedHandlers() {
    return definitionBasedHandlers;
  }

  public ActivityImportIndexHandler getActivityImportIndexHandler() {
    return activityImportIndexHandler;
  }

  public FinishedProcessInstanceImportIndexHandler getFinishedProcessInstanceImportIndexHandler() {
    return finishedProcessInstanceImportIndexHandler;
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler() {
    return processDefinitionImportIndexHandler;
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler() {
    return processDefinitionXmlImportIndexHandler;
  }

  public UnfinishedProcessInstanceImportIndexHandler getUnfinishedProcessInstanceImportIndexHandler() {
    return unfinishedProcessInstanceImportIndexHandler;
  }

  public VariableInstanceImportIndexHandler getVariableInstanceImportIndexHandler() {
    return variableInstanceImportIndexHandler;
  }
}
