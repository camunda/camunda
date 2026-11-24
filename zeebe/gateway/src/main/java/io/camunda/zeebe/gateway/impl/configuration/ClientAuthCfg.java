/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import io.camunda.zeebe.util.tls.ClientCertificateMode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutual TLS settings: whether clients must present a certificate, and which ones are allowed. */
public final class ClientAuthCfg {

  private ClientCertificateMode mode = ClientCertificateMode.NONE;

  /** PEM bundle of the CAs that client certificates must be signed by; may hold several CAs. */
  private File trustedCertificatesPath;

  /** SAN entries (DNS, IP, URI, email) allowed to connect; empty allows any trusted certificate. */
  private List<String> allowedIdentities = new ArrayList<>();

  /** Also match the subject CN against {@link #allowedIdentities}. */
  private boolean commonNameFallback = false;

  public ClientCertificateMode getMode() {
    return mode;
  }

  public ClientAuthCfg setMode(final ClientCertificateMode mode) {
    this.mode = mode;
    return this;
  }

  public File getTrustedCertificatesPath() {
    return trustedCertificatesPath;
  }

  public ClientAuthCfg setTrustedCertificatesPath(final File trustedCertificatesPath) {
    this.trustedCertificatesPath = trustedCertificatesPath;
    return this;
  }

  public List<String> getAllowedIdentities() {
    return allowedIdentities;
  }

  public ClientAuthCfg setAllowedIdentities(final List<String> allowedIdentities) {
    this.allowedIdentities = allowedIdentities;
    return this;
  }

  public boolean isCommonNameFallback() {
    return commonNameFallback;
  }

  public ClientAuthCfg setCommonNameFallback(final boolean commonNameFallback) {
    this.commonNameFallback = commonNameFallback;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, trustedCertificatesPath, allowedIdentities, commonNameFallback);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClientAuthCfg that = (ClientAuthCfg) o;
    return mode == that.mode
        && commonNameFallback == that.commonNameFallback
        && Objects.equals(trustedCertificatesPath, that.trustedCertificatesPath)
        && Objects.equals(allowedIdentities, that.allowedIdentities);
  }

  @Override
  public String toString() {
    return "ClientAuthCfg{mode=%s, trustedCertificatesPath=%s, allowedIdentities=%s, commonNameFallback=%s}"
        .formatted(mode, trustedCertificatesPath, allowedIdentities, commonNameFallback);
  }
}
