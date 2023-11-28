/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessDefinitionReaderOS implements ProcessDefinitionReader {

  @Override
  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Set<String> getAllNonOnboardedProcessDefinitionKeys() {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public String getLatestVersionToKey(final String key) {
    //todo will be handled in the OPT-7230
    return null;
  }

}
