/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.ProcessDefinitionVariableNameLookupMapper;
import java.util.List;

public class ProcessDefinitionVariableNameLookupDbReader {

  private final ProcessDefinitionVariableNameLookupMapper mapper;

  public ProcessDefinitionVariableNameLookupDbReader(
      final ProcessDefinitionVariableNameLookupMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Returns all variable names recorded for the given process definition key, or an empty list if
   * none are found.
   */
  public List<String> findVariableNames(final long processDefinitionKey) {
    return mapper.findVariableNames(processDefinitionKey);
  }
}
