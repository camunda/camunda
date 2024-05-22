/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.service.db.repository.VariableRepository;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class VariableLabelReader {
  private final VariableRepository variableRepository;

  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      final List<String> processDefinitionKeys) {
    return variableRepository.getVariableLabelsByKey(processDefinitionKeys);
  }
}
