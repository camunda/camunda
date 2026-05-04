/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import io.camunda.exporter.appint.transport.Authentication.ApiKey;
import io.camunda.exporter.appint.transport.Authentication.None;
import io.camunda.exporter.appint.transport.Authentication.OAuth;
import java.io.IOException;
import java.util.function.BiConsumer;

public sealed interface Authentication permits ApiKey, None, OAuth {

  final class None implements Authentication {
    public static final None INSTANCE = new None();

    private None() {}
  }

  record ApiKey(String apiKey) implements Authentication {
    public static final String HEADER_NAME = "X-API-KEY";
  }

  record OAuth(OAuthCredentialsProvider credentialsProvider) implements Authentication {}

  /**
   * Provides OAuth credentials as request headers. Implementations must be thread-safe.
   *
   * <p>This is an exporter-local abstraction so the exporter does not depend on the camunda-client
   * library.
   */
  @FunctionalInterface
  interface OAuthCredentialsProvider {

    /**
     * Applies the credentials by emitting one or more headers via the given consumer.
     *
     * @param headerConsumer accepts (headerName, headerValue) pairs
     * @throws IOException if obtaining credentials fails (e.g. token endpoint is unreachable)
     */
    void applyCredentials(BiConsumer<String, String> headerConsumer) throws IOException;
  }
}
