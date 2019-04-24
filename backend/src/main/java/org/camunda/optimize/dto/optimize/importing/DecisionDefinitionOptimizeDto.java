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
import org.camunda.optimize.dto.optimize.query.definition.KeyDefinitionOptimizeDto;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class DecisionDefinitionOptimizeDto implements KeyDefinitionOptimizeDto  {
  private String id;
  private String key;
  private String version;
  private String name;
  private String dmn10Xml;
  private String engine;
  private String tenantId;

  public DecisionDefinitionOptimizeDto(final String id, final String key, final String version, final String name,
                                       final String engine, final String tenantId) {
    this.id = id;
    this.key = key;
    this.version = version;
    this.name = name;
    this.engine = engine;
    this.tenantId = tenantId;
  }

  public DecisionDefinitionOptimizeDto(final String id,
                                       final String dmn10Xml,
                                       final String engine,
                                       final String tenantId) {
    this.id = id;
    this.dmn10Xml = dmn10Xml;
    this.engine = engine;
    this.tenantId = tenantId;
  }
}
