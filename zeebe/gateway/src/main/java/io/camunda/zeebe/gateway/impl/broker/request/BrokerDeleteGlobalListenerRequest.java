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
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerDeleteGlobalListenerRequest extends BrokerExecuteCommand<GlobalListenerRecord> {

  private final GlobalListenerRecord requestDto;

  public BrokerDeleteGlobalListenerRequest(final GlobalListenerRecord record) {
    super(ValueType.GLOBAL_LISTENER, GlobalListenerIntent.DELETE);
    requestDto = record;
    // Since this changes a global configuration, it is necessary to route all commands to the
    // same partition, from which they will be distributed to the other ones.
    // This ensures that the order of the commands is preserved, which is important for consistency.
    // Using a distribution queue without this additional routing is not enough since ordering
    // in the queue is based on a key which is partition-dependent, so commands for different
    // partitions would be ordered differently in the queue.
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected GlobalListenerRecord toResponseDto(final DirectBuffer buffer) {
    final GlobalListenerRecord responseDto = new GlobalListenerRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
