package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.FinishedProcessInstanceCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class FinishedProcessInstanceImportIndexHandler extends DefinitionBasedImportIndexHandler {

  @Autowired
  private FinishedProcessInstanceCountFetcher engineCountFetcher;

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
