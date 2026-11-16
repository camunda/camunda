/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AwaitProcessInstanceResultMetadata
    extends MsgpackBufferValue<AwaitProcessInstanceResultMetadata> implements DbValue {

  long requestId = -1;
  int requestStreamId = -1;
  final List<DirectBuffer> fetchVariables = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getRequestId() {
    return requestId;
  }

  public AwaitProcessInstanceResultMetadata setRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamId;
  }

  public AwaitProcessInstanceResultMetadata setRequestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public List<DirectBuffer> fetchVariables() {
    return fetchVariables;
  }

  /** The variables are referenced, not copied, like the views of the legacy property. */
  public AwaitProcessInstanceResultMetadata setFetchVariables(
      final Iterable<StringValue> variables) {
    fetchVariables.clear();
    for (final StringValue variable : variables) {
      fetchVariables.add(variable.getValue());
    }
    return this;
  }

  @Override
  protected MsgpackCodec<AwaitProcessInstanceResultMetadata> codec() {
    return AwaitProcessInstanceResultMetadataCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AwaitProcessInstanceResultMetadata> ops() {
    return AwaitProcessInstanceResultMetadataOps.INSTANCE;
  }
}
