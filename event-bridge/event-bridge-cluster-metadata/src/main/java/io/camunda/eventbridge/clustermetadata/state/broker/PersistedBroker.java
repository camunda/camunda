/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.broker;

import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Per-broker replicated state stored in the {@link
 * io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies#BROKER_REGISTRY} column
 * family — the msgpack {@link DbValue} backing the broker registry (the broker counterpart of
 * {@code PersistedTopic}). The {@code brokerId} is the key, so only the epoch, status and
 * incarnation are stored in the value.
 */
public final class PersistedBroker extends UnpackedObject implements DbValue {

  private final LongProperty brokerEpochProp = new LongProperty("brokerEpoch", 0L);
  private final EnumProperty<BrokerStatus> statusProp =
      new EnumProperty<>("status", BrokerStatus.class, BrokerStatus.ACTIVE);
  private final LongProperty incarnationProp = new LongProperty("incarnation", 0L);

  public PersistedBroker() {
    super(3);
    declareProperty(brokerEpochProp).declareProperty(statusProp).declareProperty(incarnationProp);
  }

  public PersistedBroker wrap(final BrokerMetadata metadata) {
    brokerEpochProp.setValue(metadata.brokerEpoch());
    statusProp.setValue(metadata.status());
    incarnationProp.setValue(metadata.incarnation());
    return this;
  }

  public BrokerMetadata toMetadata(final int brokerId) {
    return new BrokerMetadata(
        brokerId, brokerEpochProp.getValue(), statusProp.getValue(), incarnationProp.getValue());
  }
}
