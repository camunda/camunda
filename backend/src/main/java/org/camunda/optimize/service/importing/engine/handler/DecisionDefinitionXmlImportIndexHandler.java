/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlImportIndexHandler extends DefinitionXmlImportIndexHandler {

  private static final String DECISION_DEFINITION_XML_IMPORT_INDEX_DOC_ID = "decisionDefinitionXmlImportIndex";

  private final EngineContext engineContext;

  public DecisionDefinitionXmlImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected Set<String> performSearchQuery() {
    return databaseClient.performSearchDefinitionQuery(
      DECISION_DEFINITION_INDEX_NAME,
      DECISION_DEFINITION_XML,
      DECISION_DEFINITION_ID,
      configurationService.getEngineImportDecisionDefinitionXmlMaxPageSize(),
      getEngineAlias()
    );
  }

  @Override
  protected String getDatabaseTypeForStoring() {
    return DECISION_DEFINITION_XML_IMPORT_INDEX_DOC_ID;
  }

}
