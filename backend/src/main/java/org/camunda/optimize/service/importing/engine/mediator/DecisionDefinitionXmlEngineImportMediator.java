/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionXmlImportService;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlEngineImportMediator
  extends DefinitionXmlImportMediator<DecisionDefinitionXmlImportIndexHandler, DecisionDefinitionXmlEngineDto> {

  private final DecisionDefinitionXmlFetcher engineEntityFetcher;


  public DecisionDefinitionXmlEngineImportMediator(final DecisionDefinitionXmlImportIndexHandler importIndexHandler,
                                                   final DecisionDefinitionXmlFetcher engineEntityFetcher,
                                                   final DecisionDefinitionXmlImportService importService,
                                                   final ConfigurationService configurationService,
                                                   final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected List<DecisionDefinitionXmlEngineDto> getEntities(final IdSetBasedImportPage page) {
    return engineEntityFetcher.fetchXmlsForDefinitions(page);
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.DEFINITION_XML;
  }

}
