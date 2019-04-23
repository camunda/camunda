/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.KeyDefinitionOptimizeDto;

import java.io.Serializable;

@Getter
@Setter
public class DecisionDefinitionOptimizeDto extends KeyDefinitionOptimizeDto implements Serializable, OptimizeDto {
  private String id;
  private String version;
  private String name;
  private String dmn10Xml;
  private String engine;

  public DecisionDefinitionOptimizeDto() {
  }


  public DecisionDefinitionOptimizeDto(final String id, final String key, final String version, final String name,
                                       final String dmn10Xml, final String engine) {
    this.id = id;
    this.version = version;
    this.name = name;
    this.dmn10Xml = dmn10Xml;
    this.engine = engine;
    setKey(key);
  }
}
