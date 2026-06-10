/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue;
import java.util.List;

/**
 * Implementation of {@link MessageStartCorrelationKeyLockReleaseRecordValue}.
 *
 * <p>This record drives the pull-based release lookup that lets {@code P_K = hash(correlationKey)}
 * discover when message-start instances it created via the cross-partition handshake have completed
 * on {@code P_B = hash(businessId)}, so the correlation-key locks can be released. It never resides
 * on a single partition in isolation: {@code P_K} sends a {@code QUERY} batching one {@link
 * MessageStartLockReleaseHolder holder} per lock entry to {@code P_B} (derived from each holder's
 * instance key); {@code P_B} replies {@code RELEASE} back to {@code P_K} (derived from {@link
 * #requestKeyProp the request key}) with the single gone holder.
 */
public final class MessageStartCorrelationKeyLockReleaseRecord extends UnifiedRecordValue
    implements MessageStartCorrelationKeyLockReleaseRecordValue {

  private final LongProperty requestKeyProp = new LongProperty("requestKey", -1L);
  private final ArrayProperty<MessageStartLockReleaseHolder> holdersProp =
      new ArrayProperty<>("holders", MessageStartLockReleaseHolder::new);

  public MessageStartCorrelationKeyLockReleaseRecord() {
    super(2);
    declareProperty(requestKeyProp).declareProperty(holdersProp);
  }

  @Override
  public long getRequestKey() {
    return requestKeyProp.getValue();
  }

  public MessageStartCorrelationKeyLockReleaseRecord setRequestKey(final long requestKey) {
    requestKeyProp.setValue(requestKey);
    return this;
  }

  /**
   * This method copies each element before returning it; prefer {@link #hasHolders()} before
   * calling it. {@inheritDoc}
   */
  @Override
  public List<MessageStartLockReleaseHolderValue> getHolders() {
    // copy each element while iterating: the inner value is reused across iteration steps
    return holdersProp.stream()
        .map(
            element -> {
              final var copy = new MessageStartLockReleaseHolder();
              copy.copy(element);
              return (MessageStartLockReleaseHolderValue) copy;
            })
        .toList();
  }

  /** Returns {@code true} if this record carries at least one holder. */
  @JsonIgnore
  public boolean hasHolders() {
    return !holdersProp.isEmpty();
  }

  /**
   * Appends a new, empty holder to this record and returns it for population. Mirrors the {@code
   * add()}-then-set pattern used by other array-backed records.
   */
  public MessageStartLockReleaseHolder addHolder() {
    return holdersProp.add();
  }
}
