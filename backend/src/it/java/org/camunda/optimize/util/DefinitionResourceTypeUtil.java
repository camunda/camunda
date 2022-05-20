/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.util;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.DefinitionType;

import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;

public class DefinitionResourceTypeUtil {
  private static final Map<Integer, DefinitionType> BY_RESOURCE_TYPE_MAPPING =
    ImmutableMap.of(RESOURCE_TYPE_PROCESS_DEFINITION, PROCESS, RESOURCE_TYPE_DECISION_DEFINITION, DECISION);
  private static final Map<DefinitionType, Integer> BY_DEFINITION_TYPE_MAPPING =
    BY_RESOURCE_TYPE_MAPPING.entrySet().stream().collect(
      Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (oldValue, newValue) -> oldValue)
    );

  public static DefinitionType getDefinitionTypeByResourceType(final int resourceType) {
    return BY_RESOURCE_TYPE_MAPPING.get(resourceType);
  }

  public static int getResourceTypeByDefinitionType(final DefinitionType definitionType) {
    return BY_DEFINITION_TYPE_MAPPING.get(definitionType);
  }
}
