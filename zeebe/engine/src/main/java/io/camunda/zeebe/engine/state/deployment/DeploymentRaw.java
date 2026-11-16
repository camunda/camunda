/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

/** DeplyomentRecord wrapper to store the record in the State. */
@MsgpackPojo
@NullMarked
public final class DeploymentRaw extends MsgpackBufferValue<DeploymentRaw> implements DbValue {

  final DeploymentRecord deploymentRecord = new DeploymentRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public DeploymentRecord getDeploymentRecord() {
    return deploymentRecord;
  }

  public void setDeploymentRecord(final DeploymentRecord deploymentRecord) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = deploymentRecord.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    deploymentRecord.write(valueBuffer, 0);
    this.deploymentRecord.wrap(valueBuffer, 0, encodedLength);
  }

  @Override
  protected MsgpackCodec<DeploymentRaw> codec() {
    return DeploymentRawCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DeploymentRaw> ops() {
    return DeploymentRawOps.INSTANCE;
  }
}
