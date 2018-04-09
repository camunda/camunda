package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceImportIndexHandler extends DefinitionBasedImportIndexHandler {

  private FinishedProcessInstanceFetcher engineCountFetcher;

  public FinishedProcessInstanceImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected void init() {
    this.engineCountFetcher = beanHelper.getInstance(FinishedProcessInstanceFetcher.class, this.engineContext);
    super.init();
  }

  @Override
  protected Optional<OffsetDateTime> retrieveMaxTimestamp(String processDefinitionId) {
    try {
      return engineCountFetcher.fetchLatestEndTimeOfHistoricProcessInstances(processDefinitionId);
    } catch (Exception e) {
      logger.error("Could not fetch max time timestamp for process definition [{}]", processDefinitionId);
      return Optional.empty();
    }
  }

  @Override
  protected String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }

}
