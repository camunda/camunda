/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.security.keyvault.secrets.models.SecretProperties;
import java.time.Instant;

/**
 * The state of a Key Vault secret, from its {@code enabled}, {@code expiresOn} and {@code
 * notBefore} properties. Key Vault treats both dates as informational, so the store applies them
 * itself. Resolution and listing share this rule, so a secret listed as {@link #ACTIVE} resolves.
 */
enum SecretStatus {
  ACTIVE,
  DISABLED,
  EXPIRED,
  NOT_YET_ACTIVE;

  /**
   * Disabled wins over expired, which wins over not active yet. A secret expires at its expiry date
   * and becomes active at its activation date.
   *
   * @param now what the dates are compared with
   */
  static SecretStatus of(final SecretProperties properties, final Instant now) {
    if (Boolean.FALSE.equals(properties.isEnabled())) {
      return DISABLED;
    }
    final var expiresOn = properties.getExpiresOn();
    if (expiresOn != null && !expiresOn.toInstant().isAfter(now)) {
      return EXPIRED;
    }
    final var notBefore = properties.getNotBefore();
    if (notBefore != null && notBefore.toInstant().isAfter(now)) {
      return NOT_YET_ACTIVE;
    }
    return ACTIVE;
  }
}
