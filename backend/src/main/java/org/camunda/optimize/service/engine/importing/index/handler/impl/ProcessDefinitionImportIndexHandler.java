package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.count.ProcessDefinitionCountFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.count.UnfinishedProcessInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.util.BeanHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionImportIndexHandler extends DefinitionBasedImportIndexHandler {

  private ProcessDefinitionCountFetcher engineCountFetcher;

  public ProcessDefinitionImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    this.engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionFetcher.class, this.engineContext);
    engineCountFetcher = beanHelper.getInstance(ProcessDefinitionCountFetcher.class, this.engineContext);
    super.init();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    return engineCountFetcher.fetchProcessDefinitionCount(processDefinitionId);
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return engineCountFetcher.fetchProcessDefinitionCount(getAllProcessDefinitions());
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getProcessDefinitionType();
  }
}
