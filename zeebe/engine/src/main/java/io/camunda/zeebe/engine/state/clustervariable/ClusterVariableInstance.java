/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;

public final class ClusterVariableInstance extends UnpackedObject implements DbValue {

  private final ObjectProperty<ClusterVariableRecord> clusterVariable =
      new ObjectProperty<>("clusterVariable", new ClusterVariableRecord());

  public ClusterVariableInstance() {
    super(1);
    declareProperty(clusterVariable);
  }

  public void setRecord(final ClusterVariableRecord clusterVariableRecord) {
    clusterVariable.getValue().copyFrom(clusterVariableRecord);
  }
}
