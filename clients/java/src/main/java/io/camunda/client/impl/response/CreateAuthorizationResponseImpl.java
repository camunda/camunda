/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.response;

import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.protocol.rest.AuthorizationCreateResult;

public class CreateAuthorizationResponseImpl implements CreateAuthorizationResponse {

  private long authorizationKey;

  @Override
  public long getAuthorizationKey() {
    return authorizationKey;
  }

  public CreateAuthorizationResponseImpl setResponse(final AuthorizationCreateResult response) {
    authorizationKey = Long.parseLong(response.getAuthorizationKey());
    return this;
  }
}
