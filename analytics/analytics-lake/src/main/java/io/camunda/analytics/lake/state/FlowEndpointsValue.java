/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.analytics.lake.state.TranslatorState.FlowEndpoints;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for a {@link FlowEndpoints} — see {@link OpenElementValue}'s own javadoc
 * for the same UTF-8-caching-on-{@code set} pattern this mirrors.
 */
final class FlowEndpointsValue implements DbValue {

  private static final byte[] EMPTY = new byte[0];

  private byte[] sourceElementId = EMPTY;
  private byte[] targetElementId = EMPTY;

  FlowEndpointsValue set(final FlowEndpoints endpoints) {
    sourceElementId = endpoints.sourceElementId().getBytes(UTF_8);
    targetElementId = endpoints.targetElementId().getBytes(UTF_8);
    return this;
  }

  FlowEndpoints toRecord() {
    return new FlowEndpoints(
        new String(sourceElementId, UTF_8), new String(targetElementId, UTF_8));
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    sourceElementId = LakeValueCodec.getBytes(buffer, offset);
    offset += LakeValueCodec.sizeOf(sourceElementId);
    targetElementId = LakeValueCodec.getBytes(buffer, offset);
  }

  @Override
  public int getLength() {
    return LakeValueCodec.sizeOf(sourceElementId) + LakeValueCodec.sizeOf(targetElementId);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    offset = LakeValueCodec.putBytes(buffer, offset, sourceElementId);
    LakeValueCodec.putBytes(buffer, offset, targetElementId);
    return getLength();
  }
}
