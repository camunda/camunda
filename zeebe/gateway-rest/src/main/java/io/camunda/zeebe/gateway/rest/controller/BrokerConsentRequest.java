/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import org.agrona.DirectBuffer;

/** A consent command, routed by its key, or to the deployment partition when it has none. */
final class BrokerConsentRequest extends BrokerExecuteCommand<ConsentRecord> {

  private final ConsentRecord requestDto;

  BrokerConsentRequest(final ConsentIntent intent, final long key, final ConsentRecord record) {
    super(ValueType.CONSENT, intent);
    requestDto = record;
    if (key > 0) {
      request.setKey(key);
    } else {
      setPartitionId(Protocol.DEPLOYMENT_PARTITION);
    }
  }

  @Override
  public ConsentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ConsentRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new ConsentRecord();
    response.wrap(buffer);
    return response;
  }
}
