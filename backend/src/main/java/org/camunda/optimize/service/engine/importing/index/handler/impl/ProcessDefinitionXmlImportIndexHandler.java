package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.ProcessDefinitionCountFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionXmlImportIndexHandler extends AllEntitiesBasedImportIndexHandler {

  @Autowired
  private ProcessDefinitionCountFetcher engineCountFetcher;

  @Override
  protected String getElasticsearchImportIndexType() {
    return configurationService.getProcessDefinitionXmlType();
  }

  @Override
  protected long fetchMaxEntityCount() {
    return engineCountFetcher.fetchProcessDefinitionCount();
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionXmlMaxPageSize();
  }
}
