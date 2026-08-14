/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.AsyncRequestRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;

public final class AsyncRequestRecord extends UnifiedRecordValue
    implements AsyncRequestRecordValue {

  private final LongProperty scopeKeyProperty = new LongProperty("scopeKey", -1);
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);
  private final LongProperty operationReferenceProperty =
      new LongProperty("operationReference", -1);
  private final LongProperty batchOperationReferenceProperty =
      new LongProperty("batchOperationReference", -1);
  private final StringProperty authorizedUsernameProperty =
      new StringProperty("authorizedUsername", "");
  private final StringProperty authorizedClientIdProperty =
      new StringProperty("authorizedClientId", "");
  private final BooleanProperty authorizedAnonymousUserProperty =
      new BooleanProperty("authorizedAnonymousUser", false);

  public AsyncRequestRecord() {
    super(10);
    declareProperty(scopeKeyProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty)
        .declareProperty(operationReferenceProperty)
        .declareProperty(batchOperationReferenceProperty)
        .declareProperty(authorizedUsernameProperty)
        .declareProperty(authorizedClientIdProperty)
        .declareProperty(authorizedAnonymousUserProperty);
  }

  public void wrap(final AsyncRequestRecord record) {
    scopeKeyProperty.setValue(record.getScopeKey());
    valueTypeProperty.setValue(record.getValueType());
    intentProperty.setValue(record.getIntent().value());
    requestIdProperty.setValue(record.getRequestId());
    requestStreamIdProperty.setValue(record.getRequestStreamId());
    operationReferenceProperty.setValue(record.getOperationReference());
    batchOperationReferenceProperty.setValue(record.getBatchOperationReference());
    authorizedUsernameProperty.setValue(record.getAuthorizedUsername());
    authorizedClientIdProperty.setValue(record.getAuthorizedClientId());
    authorizedAnonymousUserProperty.setValue(record.getAuthorizedAnonymousUser());
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public AsyncRequestRecord setScopeKey(final long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  @Override
  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  public AsyncRequestRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  @Override
  public Intent getIntent() {
    return getIntent(intentProperty.getValue());
  }

  public AsyncRequestRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  private Intent getIntent(final int intentValue) {
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  @Override
  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  public AsyncRequestRecord setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  public AsyncRequestRecord setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }

  @Override
  public long getOperationReference() {
    return operationReferenceProperty.getValue();
  }

  public AsyncRequestRecord setOperationReference(final long operationReference) {
    operationReferenceProperty.setValue(operationReference);
    return this;
  }

  @Override
  public long getBatchOperationReference() {
    return batchOperationReferenceProperty.getValue();
  }

  public AsyncRequestRecord setBatchOperationReference(final long batchOperationReference) {
    batchOperationReferenceProperty.setValue(batchOperationReference);
    return this;
  }

  @Override
  public String getAuthorizedUsername() {
    return BufferUtil.bufferAsString(authorizedUsernameProperty.getValue());
  }

  @Override
  public String getAuthorizedClientId() {
    return BufferUtil.bufferAsString(authorizedClientIdProperty.getValue());
  }

  @Override
  public boolean getAuthorizedAnonymousUser() {
    return authorizedAnonymousUserProperty.getValue();
  }

  public AsyncRequestRecord setActor(final Map<String, Object> claims) {
    final var clientId = claims.get(Authorization.AUTHORIZED_CLIENT_ID);
    final var username = claims.get(Authorization.AUTHORIZED_USERNAME);
    if (clientId instanceof final String id && !id.isEmpty()) {
      authorizedClientIdProperty.setValue(id);
    } else if (username instanceof final String name && !name.isEmpty()) {
      authorizedUsernameProperty.setValue(name);
    } else if (Boolean.TRUE.equals(claims.get(Authorization.AUTHORIZED_ANONYMOUS_USER))) {
      authorizedAnonymousUserProperty.setValue(true);
    }
    return this;
  }

  public Map<String, Object> getActorClaims() {
    if (!getAuthorizedClientId().isEmpty()) {
      return Map.of(Authorization.AUTHORIZED_CLIENT_ID, getAuthorizedClientId());
    }
    if (!getAuthorizedUsername().isEmpty()) {
      return Map.of(Authorization.AUTHORIZED_USERNAME, getAuthorizedUsername());
    }
    if (getAuthorizedAnonymousUser()) {
      return Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true);
    }
    return Map.of();
  }
}
