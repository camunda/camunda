/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;

/** Plain data nested in {@link PojoRequired}, stores the same bytes as {@link Pojo.Nested}. */
@MsgpackPojo
public final class PojoRequiredNested extends MsgpackBufferValue<PojoRequiredNested> {
  @Override
  protected MsgpackCodec<PojoRequiredNested> codec() {
    return PojoRequiredNestedCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoRequiredNested> ops() {
    return PojoRequiredNestedOps.INSTANCE;
  }

  long foo = -1L;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();
}
