/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.KeyDefinitionOptimizeDto;

import java.io.Serializable;

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

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDmn10Xml() {
    return dmn10Xml;
  }

  public void setDmn10Xml(String dmn10Xml) {
    this.dmn10Xml = dmn10Xml;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }
}
