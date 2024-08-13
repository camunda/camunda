/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.CorrelateMessageResponse;
import io.camunda.zeebe.client.protocol.rest.MessageCorrelationResponse;

public final class CorrelateMessageResponseImpl implements CorrelateMessageResponse {

  private final JsonMapper jsonMapper;
  private long key;
  private String tenantId;
  private long processInstanceKey;

  public CorrelateMessageResponseImpl(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Long getMessageKey() {
    return key;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CorrelateMessageResponseImpl setResponse(final MessageCorrelationResponse response) {
    key = response.getKey();
    tenantId = response.getTenantId();
    processInstanceKey = response.getProcessInstanceKey();
    return this;
  }
}
