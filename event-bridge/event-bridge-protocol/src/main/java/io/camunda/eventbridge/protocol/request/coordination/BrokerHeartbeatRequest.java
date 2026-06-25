/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * A broker's periodic liveness heartbeat to the metadata-group leader. It carries the broker's
 * assigned epoch (a stale one is fenced) and how far it has replayed the metadata log (so the
 * leader can tell it is keeping up). {@code draining} signals controlled shutdown: the leader moves
 * the broker's replicas off before it stops.
 */
public class BrokerHeartbeatRequest extends UnpackedObject {

  private final IntegerProperty brokerIdProp = new IntegerProperty("brokerId", -1);
  private final LongProperty brokerEpochProp = new LongProperty("brokerEpoch", -1L);
  private final LongProperty metadataPositionProp = new LongProperty("metadataPosition", -1L);
  private final BooleanProperty drainingProp = new BooleanProperty("draining", false);

  public BrokerHeartbeatRequest() {
    super(4);
    declareProperty(brokerIdProp)
        .declareProperty(brokerEpochProp)
        .declareProperty(metadataPositionProp)
        .declareProperty(drainingProp);
  }

  public int getBrokerId() {
    return brokerIdProp.getValue();
  }

  public BrokerHeartbeatRequest setBrokerId(final int brokerId) {
    brokerIdProp.setValue(brokerId);
    return this;
  }

  public long getBrokerEpoch() {
    return brokerEpochProp.getValue();
  }

  public BrokerHeartbeatRequest setBrokerEpoch(final long brokerEpoch) {
    brokerEpochProp.setValue(brokerEpoch);
    return this;
  }

  public long getMetadataPosition() {
    return metadataPositionProp.getValue();
  }

  public BrokerHeartbeatRequest setMetadataPosition(final long metadataPosition) {
    metadataPositionProp.setValue(metadataPosition);
    return this;
  }

  public boolean isDraining() {
    return drainingProp.getValue();
  }

  public BrokerHeartbeatRequest setDraining(final boolean draining) {
    drainingProp.setValue(draining);
    return this;
  }
}
