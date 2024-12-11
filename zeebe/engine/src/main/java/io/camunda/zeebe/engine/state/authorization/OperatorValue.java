/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.protocol.record.value.Operator;

/** Represents the operator value stored in the database state. */
public class OperatorValue extends UnpackedObject implements DbValue {

  private final EnumProperty<Operator> operatorProp =
      new EnumProperty<>("operator", Operator.class);

  public OperatorValue() {
    super(1);
    declareProperty(operatorProp);
  }

  public Operator getOperator() {
    return operatorProp.getValue();
  }

  public void setOperator(final Operator operator) {
    operatorProp.setValue(operator);
  }
}
