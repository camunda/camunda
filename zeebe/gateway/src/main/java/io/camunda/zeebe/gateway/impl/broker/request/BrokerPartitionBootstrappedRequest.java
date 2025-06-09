/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerPartitionBootstrappedRequest extends BrokerExecuteCommand<ScaleRecord> {

  private final ScaleRecord requestDto = new ScaleRecord();

  public BrokerPartitionBootstrappedRequest(final int bootstrappedPartition) {
    super(ValueType.SCALE, ScaleIntent.MARK_PARTITION_BOOTSTRAPPED);
    requestDto.setRedistributedPartitions(List.of(bootstrappedPartition));

    // set the target partition for this request
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ScaleRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ScaleRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
