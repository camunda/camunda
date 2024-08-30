/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer.variable;

import io.camunda.optimize.service.db.repository.VariableRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class VariableUpdateInstanceWriter {
  private final VariableRepository variableRepository;

  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    log.info("Deleting variable updates for [{}] processInstanceIds", processInstanceIds.size());
    variableRepository.deleteByProcessInstanceIds(processInstanceIds);
  }
}
