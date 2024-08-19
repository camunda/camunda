/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.repository.VariableRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class VariableUpdateInstanceReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(VariableUpdateInstanceReader.class);
  private final VariableRepository variableRepository;

  public VariableUpdateInstanceReader(final VariableRepository variableRepository) {
    this.variableRepository = variableRepository;
  }

  public List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(
      final Set<String> processInstanceIds) {
    log.debug(
        "Fetching variable instance updates for [{}] process instances", processInstanceIds.size());

    if (processInstanceIds.isEmpty()) {
      return Collections.emptyList();
    }
    return variableRepository.getVariableInstanceUpdatesForProcessInstanceIds(processInstanceIds);
  }
}
