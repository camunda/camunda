/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import org.agrona.DirectBuffer;

public class BrokerActivateAdHocSubProcessInstructionRequest
    extends BrokerExecuteCommand<AdHocSubProcessInstructionRecord> {

  private final AdHocSubProcessInstructionRecord requestDto =
      new AdHocSubProcessInstructionRecord();

  public BrokerActivateAdHocSubProcessInstructionRequest() {
    super(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, AdHocSubProcessInstructionIntent.ACTIVATE);
  }

  public BrokerActivateAdHocSubProcessInstructionRequest setAdHocSubProcessInstanceKey(
      final String adHocSubProcessInstanceKey) {
    requestDto.setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey);
    return this;
  }

  public BrokerActivateAdHocSubProcessInstructionRequest addElement(final String elementId) {
    requestDto.elements().add().setElementId(elementId);
    return this;
  }

  @Override
  public AdHocSubProcessInstructionRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected AdHocSubProcessInstructionRecord toResponseDto(final DirectBuffer buffer) {
    final AdHocSubProcessInstructionRecord responseDto = new AdHocSubProcessInstructionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
