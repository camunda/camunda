/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.optimize.dto;

import java.io.Serializable;

public class VariableDto implements Serializable {

  private String type;
  private Object value;

  private ValueInfo valueInfo = new ValueInfo();

  public String getType() {
    return this.type;
  }

  public Object getValue() {
    return this.value;
  }

  public ValueInfo getValueInfo() {
    return this.valueInfo;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  public void setValueInfo(final ValueInfo valueInfo) {
    this.valueInfo = valueInfo;
  }

  public static class ValueInfo {

    private String objectTypeName;
    private String serializationDataFormat;

    public String getObjectTypeName() {
      return this.objectTypeName;
    }

    public String getSerializationDataFormat() {
      return this.serializationDataFormat;
    }

    public void setObjectTypeName(final String objectTypeName) {
      this.objectTypeName = objectTypeName;
    }

    public void setSerializationDataFormat(final String serializationDataFormat) {
      this.serializationDataFormat = serializationDataFormat;
    }
  }
}
