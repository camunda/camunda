package io.camunda.zeebe.spring.client.bean;

import java.lang.reflect.Parameter;

public class ParameterInfo {

  private String parameterName;

  private Parameter parameterInfo;

  public ParameterInfo(Parameter param, String paramName) {
    if (paramName == null) {
      parameterName = param.getName();
    } else {
      this.parameterName = paramName;
    }
    this.parameterInfo = param;
  }

  public Parameter getParameterInfo() {
    return parameterInfo;
  }

  public String getParameterName() {
    return parameterName;
  }
}
