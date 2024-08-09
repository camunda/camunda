/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.handler;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;

import io.camunda.optimize.rest.engine.EngineContext;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlImportIndexHandler extends DefinitionXmlImportIndexHandler {

  private static final String PROCESS_DEFINITION_XML_IMPORT_INDEX_DOC_ID =
      "processDefinitionXmlImportIndex";

  private final EngineContext engineContext;

  public ProcessDefinitionXmlImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected Set<String> performSearchQuery() {
    return databaseClient.performSearchDefinitionQuery(
        PROCESS_DEFINITION_INDEX_NAME,
        PROCESS_DEFINITION_XML,
        PROCESS_DEFINITION_ID,
        configurationService.getEngineImportProcessDefinitionXmlMaxPageSize(),
        getEngineAlias());
  }

  @Override
  protected String getDatabaseTypeForStoring() {
    return PROCESS_DEFINITION_XML_IMPORT_INDEX_DOC_ID;
  }
}
