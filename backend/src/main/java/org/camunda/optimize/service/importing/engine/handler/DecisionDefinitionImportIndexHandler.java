/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionImportIndexHandler extends AllEntitiesBasedImportIndexHandler {

  private Set<String> alreadyImportedIds = new HashSet<>();

  private final EngineContext engineContext;

  public DecisionDefinitionImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected String getElasticsearchImportIndexType() {
    return ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
  }

  public void addImportedDefinitions(Collection<DecisionDefinitionEngineDto> definitions) {
    definitions.forEach(d -> alreadyImportedIds.add(d.getId()));
    moveImportIndex(definitions.size());
  }

  public List<DecisionDefinitionEngineDto> filterNewDefinitions(List<DecisionDefinitionEngineDto> engineEntities) {
    return engineEntities
      .stream()
      .filter(def -> !alreadyImportedIds.contains(def.getId()))
      .collect(Collectors.toList());
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    alreadyImportedIds.clear();
  }
}
