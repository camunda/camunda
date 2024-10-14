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
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class UserRecord extends UnifiedRecordValue implements UserRecordValue {
  private final LongProperty userKeyProp = new LongProperty("userKey", -1L);
  private final StringProperty usernameProp = new StringProperty("username");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty emailProp = new StringProperty("email", "");
  private final StringProperty passwordProp = new StringProperty("password", "");
  private final EnumProperty<UserType> userTypeProp =
      new EnumProperty<>("userType", UserType.class, UserType.REGULAR);

  public UserRecord() {
    super(6);
    declareProperty(userKeyProp)
        .declareProperty(usernameProp)
        .declareProperty(nameProp)
        .declareProperty(emailProp)
        .declareProperty(passwordProp)
        .declareProperty(userTypeProp);
  }

  public void wrap(final UserRecord record) {
    userKeyProp.setValue(record.getUserKey());
    usernameProp.setValue(record.getUsernameBuffer());
    nameProp.setValue(record.getNameBuffer());
    emailProp.setValue(record.getEmailBuffer());
    passwordProp.setValue(record.getPasswordBuffer());
    userTypeProp.setValue(record.getUserType());
  }

  public UserRecord copy() {
    final var copy = new UserRecord();
    copy.userKeyProp.setValue(getUserKey());
    copy.usernameProp.setValue(BufferUtil.cloneBuffer(getUsernameBuffer()));
    copy.nameProp.setValue(BufferUtil.cloneBuffer(getNameBuffer()));
    copy.emailProp.setValue(BufferUtil.cloneBuffer(getEmailBuffer()));
    copy.passwordProp.setValue(BufferUtil.cloneBuffer(getPasswordBuffer()));
    copy.userTypeProp.setValue(getUserType());
    return copy;
  }

  @Override
  public Long getUserKey() {
    return userKeyProp.getValue();
  }

  public UserRecord setUserKey(final Long userKey) {
    userKeyProp.setValue(userKey);
    return this;
  }

  @Override
  public String getUsername() {
    return bufferAsString(usernameProp.getValue());
  }

  public UserRecord setUsername(final String username) {
    usernameProp.setValue(username);
    return this;
  }

  public UserRecord setUsername(final DirectBuffer username) {
    usernameProp.setValue(username);
    return this;
  }

  @Override
  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public UserRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public UserRecord setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getEmail() {
    return bufferAsString(emailProp.getValue());
  }

  public UserRecord setEmail(final String email) {
    emailProp.setValue(email);
    return this;
  }

  public UserRecord setEmail(final DirectBuffer email) {
    emailProp.setValue(email);
    return this;
  }

  @Override
  public String getPassword() {
    return bufferAsString(passwordProp.getValue());
  }

  @Override
  public UserType getUserType() {
    return userTypeProp.getValue();
  }

  public UserRecord setUserType(final UserType userType) {
    userTypeProp.setValue(userType);
    return this;
  }

  public UserRecord setPassword(final String password) {
    passwordProp.setValue(password);
    return this;
  }

  public UserRecord setPassword(final DirectBuffer password) {
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
