/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallistener;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class PersistedGlobalListenersConfig
    extends MsgpackBufferValue<PersistedGlobalListenersConfig> implements DbValue {

  final GlobalListenerBatchRecord globalListeners = new GlobalListenerBatchRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public GlobalListenerBatchRecord getGlobalListeners() {
    return globalListeners;
  }

  public void setGlobalListeners(final GlobalListenerBatchRecord record) {
    globalListeners.copyFrom(record);
  }

  @Override
  protected MsgpackCodec<PersistedGlobalListenersConfig> codec() {
    return PersistedGlobalListenersConfigCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedGlobalListenersConfig> ops() {
    return PersistedGlobalListenersConfigOps.INSTANCE;
  }
}
