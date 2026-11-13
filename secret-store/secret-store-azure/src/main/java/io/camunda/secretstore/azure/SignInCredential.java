/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import reactor.core.publisher.Mono;

/**
 * One credential of the store's {@link AzureCredentialChain chain}, reporting to the store's {@link
 * SignInMonitor} when it obtains a token.
 *
 * <p>Errors pass through unchanged: {@link com.azure.identity.ChainedTokenCredential} moves on to
 * the next credential only on a {@link com.azure.identity.CredentialUnavailableException}, so
 * wrapping or replacing one would change which credential signs in.
 *
 * @param name the credential type, for example {@code ManagedIdentityCredential}
 * @param description what the operator reads in the log: the type, and for an identity the client
 *     ID it signs in as. Never a secret.
 */
record SignInCredential(
    String name, String description, TokenCredential delegate, SignInMonitor monitor)
    implements TokenCredential {

  @Override
  public Mono<AccessToken> getToken(final TokenRequestContext request) {
    return delegate.getToken(request).doOnNext(token -> monitor.succeeded(description));
  }

  // overridden so the synchronous SecretClient keeps the SDK's synchronous path, rather than the
  // default, which blocks on getToken
  @Override
  public AccessToken getTokenSync(final TokenRequestContext request) {
    final var token = delegate.getTokenSync(request);
    monitor.succeeded(description);
    return token;
  }

  @Override
  public String toString() {
    return description;
  }
}
