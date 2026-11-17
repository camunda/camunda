/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ClusterVariableInstance extends MsgpackBufferValue<ClusterVariableInstance>
    implements DbValue {

  final ClusterVariableRecord clusterVariable = new ClusterVariableRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void setRecord(final ClusterVariableRecord clusterVariableRecord) {
    clusterVariable.copyFrom(clusterVariableRecord);
  }

  public ClusterVariableRecord getRecord() {
    return clusterVariable;
  }

  public DirectBuffer getValueBuffer() {
    return clusterVariable.getValueBuffer();
  }

  public DirectBuffer getMetadataBuffer() {
    return clusterVariable.getMetadataBuffer();
  }

  @Override
  protected MsgpackCodec<ClusterVariableInstance> codec() {
    return ClusterVariableInstanceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ClusterVariableInstance> ops() {
    return ClusterVariableInstanceOps.INSTANCE;
  }
}
