/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
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
public class ProcessDefinitionImportIndexHandler extends AllEntitiesBasedImportIndexHandler {

  private Set<String> alreadyImportedIds = new HashSet<>();


  public ProcessDefinitionImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected String getElasticsearchImportIndexType() {
    return ElasticsearchConstants.PROC_DEF_TYPE;
  }

  public void addImportedDefinitions(Collection<ProcessDefinitionEngineDto> definitions) {
    definitions.forEach(d -> alreadyImportedIds.add(d.getId()));
    moveImportIndex(definitions.size());
  }

  public List<ProcessDefinitionEngineDto> filterNewDefinitions(List<ProcessDefinitionEngineDto> engineEntities) {
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
