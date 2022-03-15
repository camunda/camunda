/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.record.value;

import io.camunda.operate.zeebeimport.v1_4.record.RecordValueImpl;

public class EvaluatedInputValueImpl extends RecordValueImpl {

  private String inputId;
  private String inputName;
  private String inputValue;

  public String getInputId() {
    return inputId;
  }

  public String getInputName() {
    return inputName;
  }

  public String getInputValue() {
    return inputValue;
  }

  public EvaluatedInputValueImpl setInputId(final String inputId) {
    this.inputId = inputId;
    return this;
  }

  public EvaluatedInputValueImpl setInputName(final String inputName) {
    this.inputName = inputName;
    return this;
  }

  public EvaluatedInputValueImpl setInputValue(final String inputValue) {
    this.inputValue = inputValue;
    return this;
  }
}
