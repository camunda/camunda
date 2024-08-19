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
    return type;
  }

  public VariableDto setType(final String type) {
    this.type = type;
    return this;
  }

  public Object getValue() {
    return value;
  }

  public VariableDto setValue(final Object value) {
    this.value = value;
    return this;
  }

  public ValueInfo getValueInfo() {
    return valueInfo;
  }

  public VariableDto setValueInfo(final ValueInfo valueInfo) {
    this.valueInfo = valueInfo;
    return this;
  }

  public static class ValueInfo {

    private String objectTypeName;
    private String serializationDataFormat;

    public String getObjectTypeName() {
      return objectTypeName;
    }

    public ValueInfo setObjectTypeName(final String objectTypeName) {
      this.objectTypeName = objectTypeName;
      return this;
    }

    public String getSerializationDataFormat() {
      return serializationDataFormat;
    }

    public ValueInfo setSerializationDataFormat(final String serializationDataFormat) {
      this.serializationDataFormat = serializationDataFormat;
      return this;
    }
  }
}
