/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessActivityActivationIntent;
import org.agrona.DirectBuffer;

public class BrokerActivateAdHocSubprocessActivityRequest
    extends BrokerExecuteCommand<AdHocSubProcessActivityActivationRecord> {

  private final AdHocSubProcessActivityActivationRecord requestDto =
      new AdHocSubProcessActivityActivationRecord();

  public BrokerActivateAdHocSubprocessActivityRequest() {
    super(
        ValueType.AD_HOC_SUB_PROCESS_ACTIVITY_ACTIVATION,
        AdHocSubProcessActivityActivationIntent.ACTIVATE);
  }

  public BrokerActivateAdHocSubprocessActivityRequest setAdHocSubProcessInstanceKey(
      final String adHocSubProcessInstanceKey) {
    requestDto.setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey);
    return this;
  }

  public BrokerActivateAdHocSubprocessActivityRequest addElement(final String elementId) {
    requestDto.elements().add().setElementId(elementId);
    return this;
  }

  @Override
  public AdHocSubProcessActivityActivationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected AdHocSubProcessActivityActivationRecord toResponseDto(final DirectBuffer buffer) {
    final AdHocSubProcessActivityActivationRecord responseDto =
        new AdHocSubProcessActivityActivationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
