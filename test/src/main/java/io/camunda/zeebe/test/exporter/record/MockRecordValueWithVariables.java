/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter.record;

import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import java.util.Map;

public class MockRecordValueWithVariables extends MockRecordValue
    implements RecordValueWithVariables {

  private Map<String, Object> variables;

  public MockRecordValueWithVariables() {}

  public MockRecordValueWithVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public MockRecordValueWithVariables setVariables(final Map<String, Object> variables) {
    this.variables = variables;
    return this;
  }
}
