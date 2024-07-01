/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Collections;
import java.util.Set;

public abstract class DefinitionInstanceReader {

  public Set<String> getAllExistingDefinitionKeys(final DefinitionType type) {
    return getAllExistingDefinitionKeys(type, Collections.emptySet());
  }

  public abstract Set<String> getAllExistingDefinitionKeys(
      final DefinitionType type, final Set<String> instanceIds);

  protected String resolveInstanceIdFieldForType(final DefinitionType type) {
    return switch (type) {
      case PROCESS -> ProcessInstanceDto.Fields.processInstanceId;
      case DECISION -> DecisionInstanceDto.Fields.decisionInstanceId;
      default -> throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    };
  }

  protected String resolveDefinitionKeyFieldForType(final DefinitionType type) {
    return switch (type) {
      case PROCESS -> ProcessInstanceDto.Fields.processDefinitionKey;
      case DECISION -> DecisionInstanceDto.Fields.decisionDefinitionKey;
      default -> throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    };
  }

  protected String resolveIndexMultiAliasForType(final DefinitionType type) {
    return switch (type) {
      case PROCESS -> PROCESS_INSTANCE_MULTI_ALIAS;
      case DECISION -> DECISION_INSTANCE_MULTI_ALIAS;
      default -> throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    };
  }
}
