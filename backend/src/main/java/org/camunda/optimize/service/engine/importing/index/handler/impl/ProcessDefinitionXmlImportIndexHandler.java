package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.service.engine.importing.fetcher.count.ProcessDefinitionCountFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlImportIndexHandler extends DefinitionBasedImportIndexHandler {

  private ProcessDefinitionCountFetcher engineCountFetcher;

  public ProcessDefinitionXmlImportIndexHandler(String engineAlias) {
    super(engineAlias);
    this.engineAlias = engineAlias;
  }

  @PostConstruct
  public void init() {
    this.engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionFetcher.class, this.engineAlias);
    engineCountFetcher = beanHelper.getInstance(ProcessDefinitionCountFetcher.class, this.engineAlias);
    super.init();
  }

  @Override
  protected long fetchMaxEntityCountForDefinition(String processDefinitionId) {
    return engineCountFetcher.fetchProcessDefinitionCount(processDefinitionId);
  }

  @Override
  protected long getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionXmlMaxPageSize();
  }

  @Override
  protected long fetchMaxEntityCountForAllDefinitions() {
    return engineCountFetcher.fetchProcessDefinitionCount(getAllProcessDefinitions());
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getProcessDefinitionXmlType();
  }
}
