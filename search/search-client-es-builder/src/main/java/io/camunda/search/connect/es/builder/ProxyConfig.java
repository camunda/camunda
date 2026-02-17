/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es.builder;

/**
 * Immutable proxy configuration for the Elasticsearch client. Use {@link #builder()} to create
 * instances via the fluent {@link Builder}.
 */
public final class ProxyConfig {

  private final String host;
  private final int port;
  private final boolean sslEnabled;
  private final String username;
  private final String password;

  private ProxyConfig(final Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.sslEnabled = builder.sslEnabled;
    this.username = builder.username;
    this.password = builder.password;
  }

  /** Creates a new {@link Builder} for proxy configuration. */
  public static Builder builder() {
    return new Builder();
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public boolean isSslEnabled() {
    return sslEnabled;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  /** Returns true if proxy authentication credentials are configured. */
  public boolean hasAuth() {
    return username != null && !username.isEmpty() && password != null && !password.isEmpty();
  }

  /** Fluent builder for {@link ProxyConfig}. */
  public static final class Builder {

    private String host;
    private int port;
    private boolean sslEnabled;
    private String username;
    private String password;

    private Builder() {}

    /** Sets the proxy host. */
    public Builder host(final String host) {
      this.host = host;
      return this;
    }

    /** Sets the proxy port. */
    public Builder port(final int port) {
      this.port = port;
      return this;
    }

    /** Sets whether to use HTTPS for the proxy connection. */
    public Builder sslEnabled(final boolean sslEnabled) {
      this.sslEnabled = sslEnabled;
      return this;
    }

    /** Sets the proxy authentication username. */
    public Builder username(final String username) {
      this.username = username;
      return this;
    }

    /** Sets the proxy authentication password. */
    public Builder password(final String password) {
      this.password = password;
      return this;
    }

    /** Builds an immutable {@link ProxyConfig} instance. */
    public ProxyConfig build() {
      return new ProxyConfig(this);
    }
  }
}
