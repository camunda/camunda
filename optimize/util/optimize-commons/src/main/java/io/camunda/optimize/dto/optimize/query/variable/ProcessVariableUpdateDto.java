/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.NoArgsConstructor;

/**
 * This class is using the dto from the plugin system, in order to enable to enrich the variable
 * import.
 *
 * <p>Note: This class is still needed, because it implements the optimize dto opposed to the plugin
 * dto.
 */
@NoArgsConstructor
public class ProcessVariableUpdateDto extends PluginVariableDto implements OptimizeDto {
  public ProcessVariableUpdateDto(
      final String id,
      final String name,
      final String type,
      final String value,
      final OffsetDateTime timestamp,
      final Map<String, Object> valueInfo,
      final String processDefinitionKey,
      final String processDefinitionId,
      final String processInstanceId,
      final Long version,
      final String engineAlias,
      final String tenantId) {
    super(
        id,
        name,
        type,
        value,
        timestamp,
        valueInfo,
        processDefinitionKey,
        processDefinitionId,
        processInstanceId,
        version,
        engineAlias,
        tenantId);
  }
}
