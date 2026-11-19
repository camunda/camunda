/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.exception.HttpResponseException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Reads Key Vault's error responses, as shared by every {@link AzureSecretResolver}. */
final class AzureKeyVaultErrors {

  /**
   * The inner error code Key Vault answers a read of a disabled secret with, alongside HTTP 403.
   * Not in Microsoft's reference docs; observed on real Azure Key Vault.
   */
  static final String SECRET_DISABLED = "SecretDisabled";

  private AzureKeyVaultErrors() {}

  static boolean isSecretDisabled(final HttpResponseException e) {
    return e.getResponse() != null
        && e.getResponse().getStatusCode() == 403
        && SECRET_DISABLED.equals(innerErrorCode(e));
  }

  /**
   * The {@code error.innererror.code} of Key Vault's error body, which the SDK hands back parsed
   * into nested maps, or {@code null} if the body has none.
   */
  static @Nullable String innerErrorCode(final HttpResponseException e) {
    if (e.getValue() instanceof final Map<?, ?> body
        && body.get("error") instanceof final Map<?, ?> error
        && error.get("innererror") instanceof final Map<?, ?> innerError
        && innerError.get("code") instanceof final String code) {
      return code;
    }
    return null;
  }
}
