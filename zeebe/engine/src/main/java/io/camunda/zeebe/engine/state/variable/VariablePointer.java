/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class VariablePointer extends UnpackedObject implements DbValue {

  private final StringProperty nameProp = new StringProperty("name");
  private final LongProperty scopeKey = new LongProperty("scope");

  public VariablePointer() {
    super(2);
    declareProperty(scopeKey).declareProperty(nameProp);
  }

  public long getScope() {
    return scopeKey.getValue();
  }

  public VariablePointer setScope(final long scope) {
    scopeKey.setValue(scope);
    return this;
  }

  public VariablePointer setName(final DirectBuffer value, final int offset, final int length) {
    nameProp.setValue(value, offset, length);
    return this;
  }

  public DirectBuffer getName() {
    return nameProp.getValue();
  }
}
