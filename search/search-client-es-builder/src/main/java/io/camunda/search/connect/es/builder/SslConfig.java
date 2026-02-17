/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es.builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable SSL/TLS configuration for the Elasticsearch client. Use {@link #builder()} to create
 * instances via the fluent {@link Builder}.
 */
public final class SslConfig {

  private final boolean enabled;
  private final String certificatePath;
  private final List<String> certificateAuthorities;
  private final boolean selfSigned;
  private final boolean verifyHostname;

  private SslConfig(final Builder builder) {
    this.enabled = builder.enabled;
    this.certificatePath = builder.certificatePath;
    this.certificateAuthorities = List.copyOf(builder.certificateAuthorities);
    this.selfSigned = builder.selfSigned;
    this.verifyHostname = builder.verifyHostname;
  }

  /** Creates a new {@link Builder} for SSL/TLS configuration. */
  public static Builder builder() {
    return new Builder();
  }

  /** Creates an SslConfig with SSL disabled and hostname verification on (the default). */
  public static SslConfig disabled() {
    return new Builder().build();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getCertificatePath() {
    return certificatePath;
  }

  public List<String> getCertificateAuthorities() {
    return certificateAuthorities;
  }

  public boolean isSelfSigned() {
    return selfSigned;
  }

  public boolean isVerifyHostname() {
    return verifyHostname;
  }

  /** Fluent builder for {@link SslConfig}. */
  public static final class Builder {

    private boolean enabled;
    private String certificatePath;
    private List<String> certificateAuthorities = new ArrayList<>();
    private boolean selfSigned;
    private boolean verifyHostname = true;

    private Builder() {}

    /** Enables or disables SSL/TLS. */
    public Builder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /** Sets the path to a server certificate file (X.509 PEM or PKCS12 .p12/.pfx). */
    public Builder certificatePath(final String certificatePath) {
      this.certificatePath = certificatePath;
      return this;
    }

    /** Sets the list of paths to CA certificate files (X.509 PEM). */
    public Builder certificateAuthorities(final List<String> certificateAuthorities) {
      this.certificateAuthorities =
          certificateAuthorities != null
              ? new ArrayList<>(certificateAuthorities)
              : new ArrayList<>();
      return this;
    }

    /** Sets whether to trust self-signed certificates. */
    public Builder selfSigned(final boolean selfSigned) {
      this.selfSigned = selfSigned;
      return this;
    }

    /** Sets whether to verify the server hostname against the certificate. */
    public Builder verifyHostname(final boolean verifyHostname) {
      this.verifyHostname = verifyHostname;
      return this;
    }

    /** Builds an immutable {@link SslConfig} instance. */
    public SslConfig build() {
      return new SslConfig(this);
    }
  }
}
