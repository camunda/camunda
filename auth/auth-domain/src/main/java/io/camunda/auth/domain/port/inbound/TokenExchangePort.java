/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.inbound;

import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;

/** Inbound port for performing token exchange operations. */
public interface TokenExchangePort {

  /**
   * Exchanges a token according to RFC 8693 or an IdP-specific OBO flow.
   *
   * @param request the token exchange request
   * @return the token exchange response containing the new token
   */
  TokenExchangeResponse exchange(TokenExchangeRequest request);
}
