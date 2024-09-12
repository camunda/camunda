/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.CreateUserResponse;
import io.camunda.zeebe.client.protocol.rest.UserCreateResponse;

public class CreateUserResponseImpl implements CreateUserResponse {

  private final JsonMapper jsonMapper;
  private long userKey;

  public CreateUserResponseImpl(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public long getUserKey() {
    return userKey;
  }

  public CreateUserResponseImpl setResponse(final UserCreateResponse response) {
    userKey = response.getUserKey();
    return this;
  }
}
