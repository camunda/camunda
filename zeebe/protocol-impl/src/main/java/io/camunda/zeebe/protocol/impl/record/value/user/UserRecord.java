/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.user;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackSanitized;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class UserRecord extends MsgpackBufferValue<UserRecord>
    implements MsgpackRecordValue, UserRecordValue {
  long userKey = -1L;
  @Nullable DirectBuffer username;
  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer email = Decoding.EMPTY;
  @MsgpackSanitized DirectBuffer password = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public UserRecord copy() {
    final UserRecord copy = new UserRecord();
    copy.copyFrom(this);
    return copy;
  }

  @Override
  public Long getUserKey() {
    return userKey;
  }

  public UserRecord setUserKey(final long userKey) {
    this.userKey = userKey;
    return this;
  }

  @Override
  public String getUsername() {
    return bufferAsString(Decoding.required(username, "username"));
  }

  public UserRecord setUsername(final String username) {
    this.username = BufferUtil.wrapString(username);
    return this;
  }

  public UserRecord setUsername(final DirectBuffer username) {
    this.username = username;
    return this;
  }

  @Override
  public String getName() {
    return bufferAsString(name);
  }

  public UserRecord setName(final String name) {
    if (name == null) {
      return this;
    }
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  public UserRecord setName(final DirectBuffer name) {
    this.name = name;
    return this;
  }

  @Override
  public String getEmail() {
    return bufferAsString(email);
  }

  public UserRecord setEmail(final String email) {
    if (email == null) {
      return this;
    }
    this.email = BufferUtil.wrapString(email);
    return this;
  }

  public UserRecord setEmail(final DirectBuffer email) {
    this.email = email;
    return this;
  }

  @Override
  public String getPassword() {
    return bufferAsString(password);
  }

  public UserRecord setPassword(final String password) {
    this.password = BufferUtil.wrapString(password);
    return this;
  }

  public UserRecord setPassword(final DirectBuffer password) {
    this.password = password;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getUsernameBuffer() {
    return Decoding.required(username, "username");
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return name;
  }

  @JsonIgnore
  public DirectBuffer getEmailBuffer() {
    return email;
  }

  @JsonIgnore
  public DirectBuffer getPasswordBuffer() {
    return password;
  }

  @Override
  protected MsgpackCodec<UserRecord> codec() {
    return UserRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<UserRecord> ops() {
    return UserRecordOps.INSTANCE;
  }
}
