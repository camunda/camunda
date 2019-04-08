/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

public class DecisionDefinitionXmlEngineDto implements EngineDto {

  protected String id;
  protected String dmnXml;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDmnXml() {
    return dmnXml;
  }

  public void setDmnXml(String dmnXml) {
    this.dmnXml = dmnXml;
  }
}
