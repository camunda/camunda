/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;

import java.util.HashMap;
import java.util.Map;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProcessDefinitionOptimizeDto implements DefinitionOptimizeDto {
  private String id;
  private String key;
  private String version;
  private String name;
  private String engine;
  private String tenantId;
  private String bpmn20Xml;
  private Map<String, String> flowNodeNames = new HashMap<>();
  private Map<String, String> userTaskNames = new HashMap<>();

  public ProcessDefinitionOptimizeDto(final String id,
                                      final String key,
                                      final String version,
                                      final String name,
                                      final String engine,
                                      final String tenantId) {
    this.id = id;
    this.key = key;
    this.version = version;
    this.name = name;
    this.engine = engine;
    this.tenantId = tenantId;
  }

  public ProcessDefinitionOptimizeDto(final String id,
                                      final String engine,
                                      final String bpmn20Xml,
                                      final Map<String, String> flowNodeNames,
                                      final Map<String, String> userTaskNames) {
    this.id = id;
    this.engine = engine;
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeNames = flowNodeNames;
    this.userTaskNames = userTaskNames;
  }
}
