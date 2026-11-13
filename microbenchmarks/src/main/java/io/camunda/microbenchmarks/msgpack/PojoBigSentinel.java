/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.microbenchmarks.msgpack.Pojo.POJOEnum;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackRequired;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * {@link PojoBig} with {@code @MsgpackRequired} primitives for the properties without a default,
 * unset while 0.
 */
@MsgpackPojo
public final class PojoBigSentinel extends MsgpackBufferValue<PojoBigSentinel> {
  @Override
  protected MsgpackCodec<PojoBigSentinel> codec() {
    return PojoBigSentinelCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoBigSentinel> ops() {
    return PojoBigSentinelOps.INSTANCE;
  }

  @MsgpackRequired long processInstanceKey;
  long flowScopeKey = -1L;
  @MsgpackRequired long elementInstanceKey;
  long parentProcessInstanceKey = -1L;
  long parentElementInstanceKey = -1L;
  long rootProcessInstanceKey = -1L;
  @MsgpackRequired long processDefinitionKey;
  long jobKey = -1L;
  long deadline = -1L;
  int version = -1;
  @MsgpackRequired int partitionId;
  int retries = 0;
  int loopCounter = 0;
  int activeTokens = 0;
  boolean interrupted = false;
  POJOEnum kind = POJOEnum.FOO;
  DirectBuffer processId = Decoding.EMPTY;
  DirectBuffer elementId = Decoding.EMPTY;
  DirectBuffer tenantId = Decoding.EMPTY;
  DirectBuffer errorMessage = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoBigSentinel fill(final int seed) {
    processInstanceKey = 2251799813685249L + seed;
    flowScopeKey = 2251799813685249L + seed;
    elementInstanceKey = 2251799813685300L + seed;
    processDefinitionKey = 2251799813685100L + seed;
    jobKey = 2251799813685400L + seed;
    version = 3;
    partitionId = 1 + seed % 3;
    processId = BufferUtil.wrapString("process-" + (seed % 10));
    elementId = BufferUtil.wrapString("task-" + (seed % 7));
    tenantId = BufferUtil.wrapString("<default>");
    return this;
  }
}
