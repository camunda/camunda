package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.count.FinishedProcessInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceImportIndexHandler extends DefinitionBasedImportIndexHandler {

  private FinishedProcessInstanceCountFetcher engineCountFetcher;

  public FinishedProcessInstanceImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected void init() {
    this.engineCountFetcher = beanHelper.getInstance(FinishedProcessInstanceCountFetcher.class, this.engineContext);
    super.init();
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    return engineCountFetcher.fetchCountForDefinition(processDefinitionId);
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return engineCountFetcher.fetchAllFinishedHistoricProcessInstanceCount();
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    engineCountFetcher.reset();
  }
}
