/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerCreateBatchOperationRequest
    extends BrokerExecuteCommand<Long> {

  private List<Long> keys;
  private CommandType commandType;

  public BrokerCreateBatchOperationRequest() {
    super(ValueType.BATCH_OPERATION, BatchOperationIntent.CREATE);
  }

  public BrokerCreateBatchOperationRequest setKeys(final List<Long> keys) {
    this.keys = keys;
    return this;
  }

  public BrokerCreateBatchOperationRequest setCommandType(final CommandType commandType) {
    this.commandType = commandType;
    return this;
  }

  @Override
  public ProcessInstanceRecord getRequestWriter() {
    return null; // TODO
  }

  @Override
  protected Long toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceRecord responseDto = new ProcessInstanceRecord();
    responseDto.wrap(buffer);
    return -1L; // TODO
  }

  public enum CommandType {
    CANCEL_PROCESS_INSTANCE,
  }
}
