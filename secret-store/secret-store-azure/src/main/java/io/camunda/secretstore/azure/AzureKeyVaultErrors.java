/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.exception.HttpResponseException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Reads Key Vault's error responses. */
final class AzureKeyVaultErrors {

  /**
   * The inner error code Key Vault answers a read of a disabled secret with, alongside HTTP 403.
   * Not in Microsoft's reference docs; observed on real Azure Key Vault.
   */
  private static final String SECRET_DISABLED = "SecretDisabled";

  private AzureKeyVaultErrors() {}

  static boolean isSecretDisabled(final HttpResponseException e) {
    return e.getResponse() != null
        && e.getResponse().getStatusCode() == 403
        && hasInnerErrorCode(e, SECRET_DISABLED);
  }

  /**
   * Checks whether any {@code innererror} object nested under {@code error} in Key Vault's OData
   * error body carries {@code expectedCode}. Key Vault's {@code Error} model nests {@code
   * innererror} recursively from less specific to more specific codes.
   */
  private static boolean hasInnerErrorCode(
      final HttpResponseException e, final String expectedCode) {
    if (!(e.getValue() instanceof final Map<?, ?> body)
        || !(body.get("error") instanceof Map<?, ?> current)) {
      return false;
    }
    final Set<Map<?, ?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    while (seen.add(current) && current.get("innererror") instanceof final Map<?, ?> inner) {
      if (inner.get("code") instanceof final String code
          && expectedCode.equalsIgnoreCase(code.strip())) {
        return true;
      }
      current = inner;
    }
    return false;
  }
}
