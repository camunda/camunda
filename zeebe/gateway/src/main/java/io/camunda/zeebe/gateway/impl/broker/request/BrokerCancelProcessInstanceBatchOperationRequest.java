/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;

public class BrokerCancelProcessInstanceBatchOperationRequest
    extends BrokerExecuteCommand<Long> {

  private ProcessInstanceFilter requestQueryDto;

  public BrokerCancelProcessInstanceBatchOperationRequest() {
    super(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL_BATCH_OPERATION);
  }

  public BrokerCancelProcessInstanceBatchOperationRequest setQuery(final ProcessInstanceQuery query) {
    requestQueryDto = query;
    return this;
  }

  @Override
  public ProcessInstanceRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected Long toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceRecord responseDto = new ProcessInstanceRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
