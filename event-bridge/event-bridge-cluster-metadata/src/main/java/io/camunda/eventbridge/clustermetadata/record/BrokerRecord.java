/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.record;

import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;

/**
 * The metadata stream's broker-registry record (first-class {@link ValueType#EVENT_BRIDGE_BROKER}):
 * a broker's node id, its assigned monotonic epoch, its proposed incarnation and its liveness
 * {@link BrokerStatus}. Command processors resolve/stamp these (the leader assigns the epoch);
 * appliers write the event value verbatim.
 */
public final class BrokerRecord extends UnifiedRecordValue {

  private final IntegerProperty brokerIdProp = new IntegerProperty("brokerId", -1);
  private final LongProperty brokerEpochProp = new LongProperty("brokerEpoch", 0L);
  private final EnumProperty<BrokerStatus> statusProp =
      new EnumProperty<>("status", BrokerStatus.class, BrokerStatus.ACTIVE);
  private final LongProperty incarnationProp = new LongProperty("incarnation", 0L);

  public BrokerRecord() {
    super(4);
    declareProperty(brokerIdProp)
        .declareProperty(brokerEpochProp)
        .declareProperty(statusProp)
        .declareProperty(incarnationProp);
  }

  @Override
  public ValueType valueType() {
    return MetadataRecordValues.BROKER_VALUE_TYPE;
  }

  public int getBrokerId() {
    return brokerIdProp.getValue();
  }

  public BrokerRecord setBrokerId(final int brokerId) {
    brokerIdProp.setValue(brokerId);
    return this;
  }

  public long getBrokerEpoch() {
    return brokerEpochProp.getValue();
  }

  public BrokerRecord setBrokerEpoch(final long brokerEpoch) {
    brokerEpochProp.setValue(brokerEpoch);
    return this;
  }

  public BrokerStatus getStatus() {
    return statusProp.getValue();
  }

  public BrokerRecord setStatus(final BrokerStatus status) {
    statusProp.setValue(status);
    return this;
  }

  public long getIncarnation() {
    return incarnationProp.getValue();
  }

  public BrokerRecord setIncarnation(final long incarnation) {
    incarnationProp.setValue(incarnation);
    return this;
  }

  public BrokerMetadata toMetadata() {
    return new BrokerMetadata(getBrokerId(), getBrokerEpoch(), getStatus(), getIncarnation());
  }
}
