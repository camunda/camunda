/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class EntityTypeValue extends MsgpackBufferValue<EntityTypeValue> implements DbValue {

  @Nullable EntityType entityType;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public EntityType getEntityType() {
    return Decoding.required(entityType, "entityType");
  }

  public void setEntityType(final EntityType entityType) {
    this.entityType = entityType;
  }

  @Override
  protected MsgpackCodec<EntityTypeValue> codec() {
    return EntityTypeValueCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<EntityTypeValue> ops() {
    return EntityTypeValueOps.INSTANCE;
  }
}
