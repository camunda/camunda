/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessDefinitionReader {

  private final DefinitionReader definitionReader;

  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionByKeyAndEngineOmitXml(final String processDefinitionKey,
                                                                                          final String engineAlias) {
    return definitionReader.getDefinitionByKeyAndEngineOmitXml(
      DefinitionType.PROCESS,
      processDefinitionKey,
      engineAlias
    );
  }

  public List<ProcessDefinitionOptimizeDto> getProcessDefinitions(final boolean fullyImported,
                                                                  final boolean withXml) {
    return definitionReader.getDefinitions(DefinitionType.PROCESS, fullyImported, withXml);
  }

  public String getLatestVersionToKey(String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.PROCESS, key);
  }
}
