/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.service.db.repository.VariableRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class VariableLabelReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(VariableLabelReader.class);
  private final VariableRepository variableRepository;

  public VariableLabelReader(final VariableRepository variableRepository) {
    this.variableRepository = variableRepository;
  }

  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      final List<String> processDefinitionKeys) {
    return variableRepository.getVariableLabelsByKey(processDefinitionKeys);
  }
}
