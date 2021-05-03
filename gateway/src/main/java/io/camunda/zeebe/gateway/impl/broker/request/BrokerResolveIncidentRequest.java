/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import org.agrona.DirectBuffer;

public final class BrokerResolveIncidentRequest extends BrokerExecuteCommand<IncidentRecord> {

  private final IncidentRecord requestDto = new IncidentRecord();

  public BrokerResolveIncidentRequest(final long incidentKey) {
    super(ValueType.INCIDENT, IncidentIntent.RESOLVE);
    request.setKey(incidentKey);
  }

  @Override
  public IncidentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected IncidentRecord toResponseDto(final DirectBuffer buffer) {
    final IncidentRecord responseDto = new IncidentRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
