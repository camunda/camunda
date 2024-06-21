/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import io.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import io.camunda.optimize.service.importing.engine.handler.DecisionDefinitionXmlImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionXmlImportService;
import io.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlEngineImportMediator
    extends DefinitionXmlImportMediator<
    DecisionDefinitionXmlImportIndexHandler, DecisionDefinitionXmlEngineDto> {

  private final DecisionDefinitionXmlFetcher engineEntityFetcher;

  public DecisionDefinitionXmlEngineImportMediator(
      final DecisionDefinitionXmlImportIndexHandler importIndexHandler,
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
