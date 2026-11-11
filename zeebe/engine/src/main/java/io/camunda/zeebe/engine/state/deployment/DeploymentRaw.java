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
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * DeploymentRecord wrapper to store the record in the State.
 *
 * <p>Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class DeploymentRaw extends MsgpackBufferValue<DeploymentRaw> implements DbValue {

  final DeploymentRecord deploymentRecord = new DeploymentRecord();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public DeploymentRaw() {}

  public DeploymentRecord getDeploymentRecord() {
    return deploymentRecord;
  }

  public void setDeploymentRecord(final DeploymentRecord record) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = record.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    record.write(valueBuffer, 0);
    deploymentRecord.wrap(valueBuffer, 0, encodedLength);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    // the nested record references the buffer it is read from, so it needs its own copy
    final byte[] bytes = new byte[length];
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(new UnsafeBuffer(bytes), 0, length);
  }
}
