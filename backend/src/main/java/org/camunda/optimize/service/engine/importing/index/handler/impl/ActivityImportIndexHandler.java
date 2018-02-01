package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.count.ActivityInstanceCountFetcher;
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
public class ActivityImportIndexHandler extends DefinitionBasedImportIndexHandler {

  private ActivityInstanceCountFetcher engineCountFetcher;

  public ActivityImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected void init() {
    this.engineCountFetcher = beanHelper.getInstance(ActivityInstanceCountFetcher.class, this.engineContext);
    super.init();
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getEventType();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    return engineCountFetcher.fetchCountForDefinition(processDefinitionId);
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportActivityInstanceMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return engineCountFetcher.fetchAllHistoricActivityInstanceCount();
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    engineCountFetcher.reset();
  }
}
