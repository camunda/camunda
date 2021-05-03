/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** DeplyomentRecord wrapper to store the record in the State. */
public class DeploymentRaw extends UnpackedObject implements DbValue {

  private final ObjectProperty<DeploymentRecord> deploymentRecordProperty =
      new ObjectProperty<>("deploymentRecord", new DeploymentRecord());

  public DeploymentRaw() {
    declareProperty(deploymentRecordProperty);
  }

  public void setDeploymentRecord(final DeploymentRecord deploymentRecord) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = deploymentRecord.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    deploymentRecord.write(valueBuffer, 0);
    deploymentRecordProperty.getValue().wrap(valueBuffer, 0, encodedLength);
  }

  public DeploymentRecord getDeploymentRecord() {
    return deploymentRecordProperty.getValue();
  }
}
