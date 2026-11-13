/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.secretstore.SecretStoreRegistry;
import org.jspecify.annotations.NullMarked;

/**
 * The message that tells why a secret reference failed to resolve. It names the secret and its
 * store, never a value, and ends with what the reader does to retry.
 */
@NullMarked
final class SecretResolutionFailureMessage {

  private static final String MESSAGE =
      "Failed to resolve secret '%s' from %s. Ensure the secret exists and the store is available, then %s.";

  private static final String MESSAGE_NO_STORE_CONFIGURED =
      "Failed to resolve secret '%s'. No secret store is configured. Configure a secret store, then %s.";

  private SecretResolutionFailureMessage() {}

  /**
   * @param defaultSecretStoreConfigured whether a real store backs {@link
   *     SecretStoreRegistry#DEFAULT_STORE_ID}
   * @param retry what the reader does to retry, e.g. {@code "resolve the incident to retry"}
   */
  static String of(
      final String storeId,
      final String secretReference,
      final boolean defaultSecretStoreConfigured,
      final String retry) {
    return isNoStoreConfigured(storeId, defaultSecretStoreConfigured)
        ? MESSAGE_NO_STORE_CONFIGURED.formatted(secretReference, retry)
        : MESSAGE.formatted(secretReference, describeStore(storeId), retry);
  }

  /**
   * The {@code camunda.secrets.<name>} syntax carries no store id yet (store selection is tracked
   * under <a href="https://github.com/camunda/camunda/issues/56563">#56563</a>), so every reference
   * names the default store. Naming it in the message would tell the reader nothing while it is the
   * only store there is.
   */
  private static String describeStore(final String storeId) {
    return SecretStoreRegistry.DEFAULT_STORE_ID.equals(storeId)
        ? "the configured secret store"
        : "secret store '%s'".formatted(storeId);
  }

  /**
   * Only the default store can be unconfigured: a non-default id is not reachable yet (store
   * selection is tracked under #56563), so no reference is ever raised against one.
   */
  private static boolean isNoStoreConfigured(
      final String storeId, final boolean defaultSecretStoreConfigured) {
    return SecretStoreRegistry.DEFAULT_STORE_ID.equals(storeId) && !defaultSecretStoreConfigured;
  }
}
