/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

final class VariableScope {

  private VariableScope() {}

  /**
   * Returns {@code true} if the variable is local, i.e. its {@code scopeKey} differs from its
   * {@code processInstanceKey}.
   */
  static boolean isLocal(final VariableRecordValue value) {
    return value.getScopeKey() != value.getProcessInstanceKey();
  }
}
