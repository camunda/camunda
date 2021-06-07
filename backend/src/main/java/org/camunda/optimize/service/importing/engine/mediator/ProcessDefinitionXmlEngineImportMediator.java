/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.importing.engine.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlEngineImportMediator
  extends DefinitionXmlImportMediator<ProcessDefinitionXmlImportIndexHandler, ProcessDefinitionXmlEngineDto> {

  private final ProcessDefinitionXmlFetcher engineEntityFetcher;

  public ProcessDefinitionXmlEngineImportMediator(final ProcessDefinitionXmlImportIndexHandler importIndexHandler,
                                                  final ProcessDefinitionXmlFetcher engineEntityFetcher,
                                                  final ProcessDefinitionXmlImportService importService,
                                                  final ConfigurationService configurationService,
                                                  final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected List<ProcessDefinitionXmlEngineDto> getEntities(final IdSetBasedImportPage page) {
    return engineEntityFetcher.fetchXmlsForDefinitions(page);
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.DEFINITION_XML;
  }

}
