/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class RequestData extends MsgpackBufferValue<RequestData> implements DbValue {

  final RequiredLong requestId = new RequiredLong();
  final RequiredInt requestStreamId = new RequiredInt();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public RequestData copy() {
    final var copy = new RequestData();
    copy.setRequestIdProp(getRequestId());
    copy.setRequestStreamIdProp(getRequestStreamId());
    return copy;
  }

  public Long getRequestId() {
    return requestId.get();
  }

  public RequestData setRequestIdProp(final long requestId) {
    this.requestId.set(requestId);
    return this;
  }

  public Integer getRequestStreamId() {
    return requestStreamId.get();
  }

  public RequestData setRequestStreamIdProp(final int requestStreamId) {
    this.requestStreamId.set(requestStreamId);
    return this;
  }

  @Override
  protected MsgpackCodec<RequestData> codec() {
    return RequestDataCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<RequestData> ops() {
    return RequestDataOps.INSTANCE;
  }
}
