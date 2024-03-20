/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.bean;

import java.lang.reflect.Parameter;

public class ParameterInfo {

  private final String parameterName;

  private final Parameter parameterInfo;

  public ParameterInfo(final Parameter param, final String paramName) {
    if (paramName == null) {
      parameterName = param.getName();
    } else {
      parameterName = paramName;
    }
    parameterInfo = param;
  }

  public Parameter getParameterInfo() {
    return parameterInfo;
  }

  public String getParameterName() {
    return parameterName;
  }

  @Override
  public String toString() {
    return "ParameterInfo{"
        + "parameterName="
        + parameterName
        + ", parameterInfo="
        + parameterInfo
        + '}';
  }
}
