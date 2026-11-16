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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class UserRecordV1 extends UnifiedRecordValue implements UserRecordValue {
  private final LongProperty userKeyProp = new LongProperty("userKey", -1L);
  private final StringProperty usernameProp = new StringProperty("username");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty emailProp = new StringProperty("email", "");
  private final StringProperty passwordProp = new StringProperty("password", "").sanitized();

  public UserRecordV1() {
    super(5);
    declareProperty(userKeyProp)
        .declareProperty(usernameProp)
        .declareProperty(nameProp)
        .declareProperty(emailProp)
        .declareProperty(passwordProp);
  }

  public UserRecordV1 copy() {
    final UserRecordV1 copy = new UserRecordV1();
    copy.copyFrom(this);
    return copy;
  }

  @Override
  public Long getUserKey() {
    return userKeyProp.getValue();
  }

  public UserRecordV1 setUserKey(final long userKey) {
    userKeyProp.setValue(userKey);
    return this;
  }

  @Override
  public String getUsername() {
    return bufferAsString(usernameProp.getValue());
  }

  public UserRecordV1 setUsername(final String username) {
    usernameProp.setValue(username);
    return this;
  }

  public UserRecordV1 setUsername(final DirectBuffer username) {
    usernameProp.setValue(username);
    return this;
  }

  @Override
  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public UserRecordV1 setName(final String name) {
    if (name == null) {
      return this;
    }
    nameProp.setValue(name);
    return this;
  }

  public UserRecordV1 setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getEmail() {
    return bufferAsString(emailProp.getValue());
  }

  public UserRecordV1 setEmail(final String email) {
    if (email == null) {
      return this;
    }
    emailProp.setValue(email);
    return this;
  }

  public UserRecordV1 setEmail(final DirectBuffer email) {
    emailProp.setValue(email);
    return this;
  }

  @Override
  public String getPassword() {
    return bufferAsString(passwordProp.getValue());
  }

  public UserRecordV1 setPassword(final String password) {
    passwordProp.setValue(password);
    return this;
  }

  public UserRecordV1 setPassword(final DirectBuffer password) {
    passwordProp.setValue(password);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getUsernameBuffer() {
    return usernameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getEmailBuffer() {
    return emailProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getPasswordBuffer() {
    return passwordProp.getValue();
  }
}
