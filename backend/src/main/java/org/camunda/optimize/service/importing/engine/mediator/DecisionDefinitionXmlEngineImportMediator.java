/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import lombok.Setter;
import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.importing.ScrollBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionXmlImportService;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
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
  @Autowired
  private DecisionDefinitionResolverService decisionDefinitionResolverService;
  @Autowired
  @Setter
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;

  private DecisionDefinitionXmlFetcher engineEntityFetcher;

  private final EngineContext engineContext;

  public DecisionDefinitionXmlEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getDecisionDefinitionXmlImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(DecisionDefinitionXmlFetcher.class, engineContext);

    importService = new DecisionDefinitionXmlImportService(
      elasticsearchImportJobExecutor, engineContext, decisionDefinitionXmlWriter, decisionDefinitionResolverService
    );
  }

  @Override
  protected List<DecisionDefinitionXmlEngineDto> getEntities(final IdSetBasedImportPage page) {
    return engineEntityFetcher.fetchXmlsForDefinitions(page);
  }

}
