/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.definition;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import java.util.List;

import static org.camunda.optimize.service.util.importing.EngineConstants.DECISION_DEFINITION_ENDPOINT;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionFetcher extends DefinitionFetcher<DecisionDefinitionEngineDto> {

  public DecisionDefinitionFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected GenericType<List<DecisionDefinitionEngineDto>> getResponseType() {
    return new GenericType<>() {
    };
  }

  @Override
  protected String getDefinitionEndpoint() {
    return DECISION_DEFINITION_ENDPOINT;
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportDecisionDefinitionMaxPageSize();
  }
}
