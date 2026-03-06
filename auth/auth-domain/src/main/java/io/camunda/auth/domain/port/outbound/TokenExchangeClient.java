/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;

/**
 * SPI for token exchange. Implementations delegate to Spring Security's {@code
 * OAuth2AuthorizedClientManager} or other OAuth2 token exchange mechanisms.
 */
public interface TokenExchangeClient {

  /**
   * Exchanges a token using the configured OAuth2 provider.
   *
   * @param request the token exchange request
   * @return the token exchange response
   */
  TokenExchangeResponse exchange(TokenExchangeRequest request);
}
