package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.FinishedProcessInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.util.BeanHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceImportIndexHandler extends DefinitionBasedImportIndexHandler {

  private FinishedProcessInstanceCountFetcher engineCountFetcher;

  public FinishedProcessInstanceImportIndexHandler(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @Override
  protected void init() {
    this.engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionFetcher.class, this.engineAlias);
    this.engineCountFetcher = beanHelper.getInstance(FinishedProcessInstanceCountFetcher.class, this.engineAlias);
    super.init();
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    return engineCountFetcher.fetchFinishedHistoricProcessInstanceCount(Collections.singletonList(processDefinitionId));
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return engineCountFetcher.fetchFinishedHistoricProcessInstanceCount(getAllProcessDefinitions());
  }
}
