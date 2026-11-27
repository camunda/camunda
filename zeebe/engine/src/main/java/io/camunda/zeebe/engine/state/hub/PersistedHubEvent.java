/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.hub;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEvent;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventType;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A Hub event that partition 1 received. */
@NullMarked
public final class PersistedHubEvent extends UnpackedObject implements DbValue {

  private final LongProperty seqNoProp = new LongProperty("seqNo", 0);
  private final EnumProperty<HubEventType> typeProp =
      new EnumProperty<>("type", HubEventType.class, HubEventType.POLICY_UPDATE);
  private final EnumProperty<HubEventStatus> statusProp =
      new EnumProperty<>("status", HubEventStatus.class, HubEventStatus.BUFFERING);
  private final StringProperty reasonProp = new StringProperty("reason", "");

  public PersistedHubEvent() {
    super(4);
    declareProperty(seqNoProp)
        .declareProperty(typeProp)
        .declareProperty(statusProp)
        .declareProperty(reasonProp);
  }

  public long getSeqNo() {
    return seqNoProp.getValue();
  }

  public HubEventType getType() {
    // the property has a default, so it always has a value
    return Objects.requireNonNull(typeProp.getValue());
  }

  public HubEventStatus getStatus() {
    // the property has a default, so it always has a value
    return Objects.requireNonNull(statusProp.getValue());
  }

  /** Why the event was rejected, ran into an error or was canceled; {@code null} otherwise. */
  public @Nullable String getReason() {
    final var reason = BufferUtil.bufferAsString(reasonProp.getValue());
    return reason.isEmpty() ? null : reason;
  }

  public HubEvent toHubEvent() {
    return new HubEvent(getSeqNo(), getType(), getStatus(), getReason());
  }

  public PersistedHubEvent set(
      final long seqNo,
      final HubEventType type,
      final HubEventStatus status,
      final @Nullable String reason) {
    seqNoProp.setValue(seqNo);
    typeProp.setValue(type);
    statusProp.setValue(status);
    reasonProp.setValue(reason == null ? "" : reason);
    return this;
  }
}
