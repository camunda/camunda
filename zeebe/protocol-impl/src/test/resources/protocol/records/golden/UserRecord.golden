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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class UserRecord extends UnifiedRecordValue implements UserRecordValue {
  private final LongProperty userKeyProp = new LongProperty("userKey", -1L);
  private final StringProperty usernameProp = new StringProperty("username");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty emailProp = new StringProperty("email", "");
  private final StringProperty passwordProp = new StringProperty("password", "").sanitized();
  private final StringProperty externalIdProp = new StringProperty("externalId", "");
  private final BooleanProperty activeProp = new BooleanProperty("active", true);
  private final DocumentProperty attributesProp = new DocumentProperty("attributes");
  private final StringProperty sourceProp = new StringProperty("source", "");

  /**
   * Command-only field mask. When non-empty, an update command only changes the listed attributes
   * and leaves all others untouched; when empty, the legacy update semantics apply. Events carry it
   * cleared.
   */
  private final ArrayProperty<StringValue> changedAttributesProp =
      new ArrayProperty<>("changedAttributes", StringValue::new);

  public UserRecord() {
    super(10);
    declareProperty(userKeyProp)
        .declareProperty(usernameProp)
        .declareProperty(nameProp)
        .declareProperty(emailProp)
        .declareProperty(passwordProp)
        .declareProperty(externalIdProp)
        .declareProperty(activeProp)
        .declareProperty(changedAttributesProp)
        .declareProperty(attributesProp)
        .declareProperty(sourceProp);
  }

  public UserRecord copy() {
    final UserRecord copy = new UserRecord();
    copy.copyFrom(this);
    return copy;
  }

  @Override
  public Long getUserKey() {
    return userKeyProp.getValue();
  }

  public UserRecord setUserKey(final long userKey) {
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
    if (name == null) {
      return this;
    }
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
    if (email == null) {
      return this;
    }
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

  public UserRecord setPassword(final String password) {
    passwordProp.setValue(password);
    return this;
  }

  public UserRecord setPassword(final DirectBuffer password) {
    passwordProp.setValue(password);
    return this;
  }

  @Override
  public String getExternalId() {
    return bufferAsString(externalIdProp.getValue());
  }

  public UserRecord setExternalId(final String externalId) {
    if (externalId == null) {
      return this;
    }
    externalIdProp.setValue(externalId);
    return this;
  }

  public UserRecord setExternalId(final DirectBuffer externalId) {
    externalIdProp.setValue(externalId);
    return this;
  }

  @Override
  public boolean isActive() {
    return activeProp.getValue();
  }

  public UserRecord setActive(final boolean active) {
    activeProp.setValue(active);
    return this;
  }

  @Override
  public String getSource() {
    return bufferAsString(sourceProp.getValue());
  }

  public UserRecord setSource(final String source) {
    if (source == null) {
      return this;
    }
    sourceProp.setValue(source);
    return this;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return MsgPackConverter.convertToMap(attributesProp.getValue());
  }

  public UserRecord setAttributes(final Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      attributesProp.reset();
    } else {
      attributesProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(attributes)));
    }
    return this;
  }

  public UserRecord setAttributes(final DirectBuffer attributes) {
    attributesProp.setValue(attributes);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getAttributesBuffer() {
    return attributesProp.getValue();
  }

  /** Command-only; not part of the record value API or its JSON representation. */
  @JsonIgnore
  public Set<UserAttribute> getChangedAttributes() {
    final Set<UserAttribute> attributes = EnumSet.noneOf(UserAttribute.class);
    for (final StringValue value : changedAttributesProp) {
      attributes.add(UserAttribute.valueOf(bufferAsString(value.getValue())));
    }
    return attributes;
  }

  public UserRecord setChangedAttributes(final Collection<UserAttribute> attributes) {
    changedAttributesProp.reset();
    if (attributes != null) {
      attributes.forEach(
          attribute -> changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute.name())));
    }
    return this;
  }

  public UserRecord clearChangedAttributes() {
    changedAttributesProp.reset();
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

  @JsonIgnore
  public DirectBuffer getExternalIdBuffer() {
    return externalIdProp.getValue();
  }

  /** Attributes of a user that a masked update command can change. */
  public enum UserAttribute {
    NAME,
    EMAIL,
    PASSWORD,
    EXTERNAL_ID,
    ACTIVE,
    /** Replaces the stored attributes with the attributes of the command. */
    ATTRIBUTES,
    /**
     * Merges the attributes of the command into the stored ones: a null value removes the key, any
     * other value sets it.
     */
    ATTRIBUTES_MERGE,
    /** Adopts an unowned user by setting the source of the command. */
    SOURCE
  }
}
