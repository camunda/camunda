/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class PersistedTakenSequenceFlowKeys extends UnpackedObject implements DbValue {

  private final IntegerProperty countProperty = new IntegerProperty("count", 0);
  private final ArrayProperty<TakenSequenceFlowKey> takenSequenceFlowKeysProperty =
      new ArrayProperty<>("takenSequenceFlowKeys", TakenSequenceFlowKey::new);

  public PersistedTakenSequenceFlowKeys() {
    super(2);
    declareProperty(countProperty).declareProperty(takenSequenceFlowKeysProperty);
  }

  public int getCount() {
    return countProperty.getValue();
  }

  public PersistedTakenSequenceFlowKeys addTakenSequenceFlowKey(
      final DirectBuffer gatewayElementId, final DirectBuffer sequenceFlowElementId) {
    if (!contains(gatewayElementId, sequenceFlowElementId)) {
      takenSequenceFlowKeysProperty
          .add()
          .setGatewayElementId(gatewayElementId)
          .setSequenceFlowElementId(sequenceFlowElementId);
      countProperty.setValue(countProperty.getValue() + 1);
    }

    return this;
  }

  public boolean contains(
      final DirectBuffer gatewayElementId, final DirectBuffer sequenceFlowElementId) {
    return takenSequenceFlowKeysProperty.stream()
        .anyMatch(key -> key.matches(gatewayElementId, sequenceFlowElementId));
  }

  public boolean removeTakenSequenceFlowKey(
      final DirectBuffer gatewayElementId, final DirectBuffer sequenceFlowElementId) {
    final var iterator = takenSequenceFlowKeysProperty.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().matches(gatewayElementId, sequenceFlowElementId)) {
        iterator.remove();
        countProperty.setValue(countProperty.getValue() - 1);
        return true;
      }
    }

    return false;
  }

  public List<TakenSequenceFlowKey> getTakenSequenceFlowKeys() {
    return takenSequenceFlowKeysProperty.stream().map(TakenSequenceFlowKey::copy).toList();
  }

  public boolean isEmpty() {
    return countProperty.getValue() == 0;
  }

  public static final class TakenSequenceFlowKey extends UnpackedObject {

    private final StringProperty gatewayElementIdProperty = new StringProperty("gatewayElementId");
    private final StringProperty sequenceFlowElementIdProperty =
        new StringProperty("sequenceFlowElementId");

    public TakenSequenceFlowKey() {
      super(2);
      declareProperty(gatewayElementIdProperty).declareProperty(sequenceFlowElementIdProperty);
    }

    public TakenSequenceFlowKey setGatewayElementId(final DirectBuffer gatewayElementId) {
      gatewayElementIdProperty.setValue(BufferUtil.cloneBuffer(gatewayElementId));
      return this;
    }

    public DirectBuffer getGatewayElementId() {
      return gatewayElementIdProperty.getValue();
    }

    public TakenSequenceFlowKey setSequenceFlowElementId(final DirectBuffer sequenceFlowElementId) {
      sequenceFlowElementIdProperty.setValue(BufferUtil.cloneBuffer(sequenceFlowElementId));
      return this;
    }

    public DirectBuffer getSequenceFlowElementId() {
      return sequenceFlowElementIdProperty.getValue();
    }

    public boolean matches(
        final DirectBuffer gatewayElementId, final DirectBuffer sequenceFlowElementId) {
      return BufferUtil.equals(gatewayElementIdProperty.getValue(), gatewayElementId)
          && BufferUtil.equals(sequenceFlowElementIdProperty.getValue(), sequenceFlowElementId);
    }

    private TakenSequenceFlowKey copy() {
      return new TakenSequenceFlowKey()
          .setGatewayElementId(gatewayElementIdProperty.getValue())
          .setSequenceFlowElementId(sequenceFlowElementIdProperty.getValue());
    }
  }
}
