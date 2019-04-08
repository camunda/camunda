/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.optimize.dto;

import java.io.Serializable;

public class ComplexVariableDto implements Serializable {

  private String type;
  private Object value;

  private ValueInfo valueInfo;

  public ValueInfo getValueInfo() {
    return valueInfo;
  }

  public void setValueInfo(ValueInfo valueInfo) {
    this.valueInfo = valueInfo;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public static class ValueInfo {
    private String objectTypeName;
    private String serializationDataFormat;

    public String getObjectTypeName() {
      return objectTypeName;
    }

    public void setObjectTypeName(String objectTypeName) {
      this.objectTypeName = objectTypeName;
    }

    public String getSerializationDataFormat() {
      return serializationDataFormat;
    }

    public void setSerializationDataFormat(String serializationDataFormat) {
      this.serializationDataFormat = serializationDataFormat;
    }
  }

}
