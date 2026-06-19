/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Broker request for raising the cluster's Engine Capability Version. Routed to partition 1 (the
 * deployment partition) which acts as the activation coordinator; partition 1 then distributes
 * the {@code APPLIED} event to all partitions via the existing command distribution machinery.
 */
public class BrokerClusterVersionRaiseRequest extends BrokerExecuteCommand<ClusterVersionRecord> {

  private final ClusterVersionRecord requestDto = new ClusterVersionRecord();

  public BrokerClusterVersionRaiseRequest(final int line, final int ordinal) {
    super(ValueType.CLUSTER_VERSION, ClusterVersionIntent.RAISE);
    requestDto.setLine(line).setOrdinal(ordinal);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ClusterVersionRecord toResponseDto(final DirectBuffer buffer) {
    final ClusterVersionRecord responseDto = new ClusterVersionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
