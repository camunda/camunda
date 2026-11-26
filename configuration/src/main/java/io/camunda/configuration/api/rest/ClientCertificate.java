/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.api.rest;

import java.util.ArrayList;
import java.util.List;

/**
 * Which client certificates may use the REST API and web applications, on top of Spring's own
 * {@code server.ssl.client-auth} (want/need) and trust store, which decide whether a certificate is
 * requested and which CAs it must chain to.
 */
public class ClientCertificate {

  /**
   * Identities allowed to connect, matched exactly (ignoring ASCII case) against the Subject
   * Alternative Names of the client certificate: DNS names, IP addresses, URIs or emails. When
   * empty, any certificate accepted by the trust store is allowed. Blank entries are rejected at
   * startup. Requires {@code server.ssl.client-auth} to be {@code want} or {@code need}.
   */
  private List<String> allowedIdentities = new ArrayList<>();

  /**
   * Match the certificate subject's Common Name (CN) against the allowed identities when the
   * certificate has no Subject Alternative Names at all.
   */
  private boolean commonNameFallback = false;

  public List<String> getAllowedIdentities() {
    return allowedIdentities;
  }

  public void setAllowedIdentities(final List<String> allowedIdentities) {
    this.allowedIdentities = allowedIdentities;
  }

  public boolean isCommonNameFallback() {
    return commonNameFallback;
  }

  public void setCommonNameFallback(final boolean commonNameFallback) {
    this.commonNameFallback = commonNameFallback;
  }
}
