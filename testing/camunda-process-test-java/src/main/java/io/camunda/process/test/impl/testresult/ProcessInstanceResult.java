/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testresult;

import io.camunda.process.test.impl.assertions.AssertFormatUtil;
import java.util.HashMap;
import java.util.Map;

public class ProcessInstanceResult {

  private long processInstanceKey;
  private String processId;

  private Map<String, String> variables = new HashMap<>();

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public Map<String, String> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, String> variables) {
    this.variables = variables;
  }

  @Override
  public String toString() {
    String formattedVariables = "<None>";
    if (!variables.isEmpty()) {
      formattedVariables = AssertFormatUtil.formatVariables(variables);
    }
    return String.format(
        "Process instance: %d [process-id: '%s']\n\nVariables:\n%s",
        processInstanceKey, processId, formattedVariables);
  }
}
