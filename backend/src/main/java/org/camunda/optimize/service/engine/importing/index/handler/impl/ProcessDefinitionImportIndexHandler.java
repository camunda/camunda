package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.ProcessDefinitionCountFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.count.UnfinishedProcessInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.util.BeanHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionImportIndexHandler extends AllEntitiesBasedImportIndexHandler {

  private ProcessDefinitionCountFetcher engineCountFetcher;

  public ProcessDefinitionImportIndexHandler(String engineAlias) {
    super(engineAlias);
    this.engineAlias = engineAlias;
  }

  @PostConstruct
  public void init() {
    engineCountFetcher = beanHelper.getInstance(ProcessDefinitionCountFetcher.class, this.engineAlias);
    super.init();
  }

  @Override
  protected String getElasticsearchImportIndexType() {
    return configurationService.getProcessDefinitionType();
  }

  @Override
  protected long fetchMaxEntityCount() {
    return engineCountFetcher.fetchProcessDefinitionCount();
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionMaxPageSize();
  }
}
