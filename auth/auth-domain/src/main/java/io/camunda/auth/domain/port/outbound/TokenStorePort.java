/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.TokenMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SPI for persisting token exchange audit records. */
public interface TokenStorePort {

  /**
   * Stores a token exchange audit record.
   *
   * @param metadata the token exchange metadata
   */
  void store(TokenMetadata metadata);

  /**
   * Finds a token exchange record by its exchange ID.
   *
   * @param exchangeId the exchange ID
   * @return the metadata if found
   */
  Optional<TokenMetadata> findByExchangeId(String exchangeId);

  /**
   * Finds all token exchange records for a given subject principal.
   *
   * @param subjectPrincipalId the subject principal ID
   * @param from the start of the time range
   * @param to the end of the time range
   * @return the list of matching records
   */
  List<TokenMetadata> findBySubjectPrincipalId(
      String subjectPrincipalId, Instant from, Instant to);
}
