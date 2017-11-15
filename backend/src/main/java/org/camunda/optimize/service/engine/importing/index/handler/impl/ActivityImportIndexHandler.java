package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.ActivityInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class ActivityImportIndexHandler extends DefinitionBasedImportIndexHandler {

  @Autowired
  private ActivityInstanceCountFetcher engineCountFetcher;

  @Override
  protected String getElasticsearchType() {
    return configurationService.getEventType();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    return engineCountFetcher.fetchHistoricActivityInstanceCount(Collections.singletonList(processDefinitionId));
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportActivityInstanceMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return engineCountFetcher.fetchHistoricActivityInstanceCount(getAllProcessDefinitions());
  }
}
