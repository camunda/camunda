/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.security;

import io.camunda.zeebe.util.tls.ClientCertificateMode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutual TLS settings for a TLS server: whether peers must present a certificate, which CAs it must
 * be signed by, and which identities are allowed. The same shape is used for each server (gRPC API,
 * REST API, cluster messaging), each with its own values.
 */
public class ClientAuthentication {

  /**
   * Whether peers must present a certificate: {@code none} (default, none is requested), {@code
   * optional} (a presented certificate must be valid and allowed, a peer without one is let
   * through) or {@code required}.
   */
  private ClientCertificateMode mode = ClientCertificateMode.NONE;

  /**
   * PEM file with the CA certificates peer certificates must be signed by. May hold several CAs,
   * e.g. while rotating from one CA to another.
   */
  private File trustedCertificates;

  /**
   * Identities allowed to connect, matched exactly (ignoring ASCII case) against the Subject
   * Alternative Names of the peer certificate: DNS names, IP addresses, URIs or emails. When empty,
   * any certificate signed by a trusted CA is allowed. Blank entries are rejected at startup.
   */
  private List<String> allowedIdentities = new ArrayList<>();

  /**
   * Match the certificate subject's Common Name (CN) against the allowed identities when the
   * certificate has no Subject Alternative Names at all.
   */
  private boolean commonNameFallback = false;

  public ClientCertificateMode getMode() {
    return mode;
  }

  public void setMode(final ClientCertificateMode mode) {
    this.mode = mode;
  }

  public File getTrustedCertificates() {
    return trustedCertificates;
  }

  public void setTrustedCertificates(final File trustedCertificates) {
    this.trustedCertificates = trustedCertificates;
  }

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
