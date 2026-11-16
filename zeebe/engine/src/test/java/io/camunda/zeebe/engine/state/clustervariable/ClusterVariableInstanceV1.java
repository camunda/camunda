/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.RecordObjectValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class ClusterVariableInstanceV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<RecordObjectValue<ClusterVariableRecord>> clusterVariable =
      new ObjectProperty<>("clusterVariable", new RecordObjectValue<>(new ClusterVariableRecord()));

  public ClusterVariableInstanceV1() {
    super(1);
    declareProperty(clusterVariable);
  }

  public void setRecord(final ClusterVariableRecord clusterVariableRecord) {
    clusterVariable.getValue().get().copyFrom(clusterVariableRecord);
  }

  public ClusterVariableRecord getRecord() {
    return clusterVariable.getValue().get();
  }

  public DirectBuffer getValueBuffer() {
    return clusterVariable.getValue().get().getValueBuffer();
  }

  public DirectBuffer getMetadataBuffer() {
    return clusterVariable.getValue().get().getMetadataBuffer();
  }
}
