/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

import static io.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.DefinitionType;
import java.util.Map;
import java.util.stream.Collectors;

public class DefinitionResourceTypeUtil {
  private static final Map<Integer, DefinitionType> BY_RESOURCE_TYPE_MAPPING =
      ImmutableMap.of(
          RESOURCE_TYPE_PROCESS_DEFINITION, PROCESS, RESOURCE_TYPE_DECISION_DEFINITION, DECISION);
  private static final Map<DefinitionType, Integer> BY_DEFINITION_TYPE_MAPPING =
      BY_RESOURCE_TYPE_MAPPING.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getValue, Map.Entry::getKey, (oldValue, newValue) -> oldValue));

  public static DefinitionType getDefinitionTypeByResourceType(final int resourceType) {
    return BY_RESOURCE_TYPE_MAPPING.get(resourceType);
  }

  public static int getResourceTypeByDefinitionType(final DefinitionType definitionType) {
    return BY_DEFINITION_TYPE_MAPPING.get(definitionType);
  }
}
