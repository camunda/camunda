/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.record.value;

import io.camunda.operate.zeebeimport.v1_4.record.RecordValueImpl;

public class EvaluatedOutputValueImpl extends RecordValueImpl {

  private String outputId;
  private String outputName;
  private String outputValue;

  public String getOutputId() {
    return outputId;
  }

  public String getOutputName() {
    return outputName;
  }

  public String getOutputValue() {
    return outputValue;
  }

  public EvaluatedOutputValueImpl setOutputId(final String outputId) {
    this.outputId = outputId;
    return this;
  }

  public EvaluatedOutputValueImpl setOutputName(final String outputName) {
    this.outputName = outputName;
    return this;
  }

  public EvaluatedOutputValueImpl setOutputValue(final String outputValue) {
    this.outputValue = outputValue;
    return this;
  }
}
