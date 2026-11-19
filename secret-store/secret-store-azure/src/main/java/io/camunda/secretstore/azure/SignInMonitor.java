/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the operator how one {@link AzureKeyVaultSecretStore} signs in to Microsoft Entra ID.
 *
 * <p>One instance per store, shared by every credential in its chain, so the log names the
 * credential that actually signed in exactly once, however many tokens are later fetched or
 * refreshed. Only the credential's {@link SignInCredential#description() description} is ever
 * logged: never a token, a client secret or a certificate.
 *
 * <p>Thread-safe: tokens are fetched on the store's credential executor and on the threads calling
 * the vault.
 */
final class SignInMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(SignInMonitor.class);

  private final String vaultUrl;
  private final AtomicBoolean signedIn = new AtomicBoolean();

  SignInMonitor(final String vaultUrl) {
    this.vaultUrl = vaultUrl;
  }

  /** Called with the description of the credential that has just obtained a token. */
  void succeeded(final String credential) {
    if (signedIn.compareAndSet(false, true)) {
      LOG.info("Azure Key Vault store for '{}' signed in with {}", vaultUrl, credential);
    }
  }
}
