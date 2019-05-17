/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.DecisionDefinitionXmlImportService;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlEngineImportMediator
  extends ScrollBasedImportMediator<DecisionDefinitionXmlImportIndexHandler, DecisionDefinitionXmlEngineDto> {

  @Autowired
  private DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  private DecisionDefinitionXmlFetcher engineEntityFetcher;

  public DecisionDefinitionXmlEngineImportMediator(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getDecisionDefinitionXmlImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(DecisionDefinitionXmlFetcher.class, engineContext);

    importService = new DecisionDefinitionXmlImportService(
      elasticsearchImportJobExecutor, engineContext, decisionDefinitionXmlWriter
    );
  }

  @Override
  protected List<DecisionDefinitionXmlEngineDto> getEntities(final IdSetBasedImportPage page) {
    return engineEntityFetcher.fetchXmlsForDefinitions(page);
  }

}
