package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.ProcessDefinitionManager;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionImportIndexHandler extends DefinitionBasedImportIndexHandler {

  @Autowired
  private ProcessDefinitionManager processDefinitionManager;

  public ProcessDefinitionImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    super.init();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    // every id is unique, so there is only a single definition that can be fetched per id
    return 1L;
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return processDefinitionManager.getAvailableProcessDefinitionCount(engineContext);
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getProcessDefinitionType();
  }
}
