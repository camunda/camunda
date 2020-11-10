/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;

import java.util.Map;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class EventProcessDefinitionDto extends ProcessDefinitionOptimizeDto {

  @Builder(builderMethodName = "eventProcessBuilder")
  public EventProcessDefinitionDto(@NonNull final String id, @NonNull final String key,
                                   @NonNull final String version,
                                   final String versionTag, @NonNull final String name, final String engine,
                                   final String tenantId, @NonNull final String bpmn20Xml, final boolean deleted,
                                   @NonNull final Map<String, String> flowNodeNames,
                                   @NonNull final Map<String, String> userTaskNames) {
    super(id, key, version, versionTag, name, engine, tenantId, bpmn20Xml, deleted, flowNodeNames, userTaskNames);
  }
}
