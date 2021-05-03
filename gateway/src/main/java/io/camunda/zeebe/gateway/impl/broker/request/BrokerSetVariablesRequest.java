/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import org.agrona.DirectBuffer;

public final class BrokerSetVariablesRequest extends BrokerExecuteCommand<VariableDocumentRecord> {

  private final VariableDocumentRecord requestDto = new VariableDocumentRecord();

  public BrokerSetVariablesRequest() {
    super(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATE);
  }

  public BrokerSetVariablesRequest setElementInstanceKey(final long elementInstanceKey) {
    request.setPartitionId(Protocol.decodePartitionId(elementInstanceKey));
    requestDto.setScopeKey(elementInstanceKey);
    return this;
  }

  public BrokerSetVariablesRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerSetVariablesRequest setLocal(final boolean local) {
    final VariableDocumentUpdateSemantic updateSemantics =
        local ? VariableDocumentUpdateSemantic.LOCAL : VariableDocumentUpdateSemantic.PROPAGATE;

    requestDto.setUpdateSemantics(updateSemantics);
    return this;
  }

  @Override
  public VariableDocumentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected VariableDocumentRecord toResponseDto(final DirectBuffer buffer) {
    final VariableDocumentRecord responseDto = new VariableDocumentRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
