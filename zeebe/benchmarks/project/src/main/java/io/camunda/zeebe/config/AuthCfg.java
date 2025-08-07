/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

public final class AuthCfg {
  private AuthType type = AuthType.NONE;
  private BasicCfg basic = new BasicCfg();
  private OAuthConfig oauth = new OAuthConfig();

  public AuthType getType() {
    return type;
  }

  public void setType(final AuthType type) {
    this.type = type;
  }

  public BasicCfg getBasic() {
    return basic;
  }

  public void setBasic(final BasicCfg basic) {
    this.basic = basic;
  }

  public OAuthConfig getOauth() {
    return oauth;
  }

  public void setOauth(final OAuthConfig oauth) {
    this.oauth = oauth;
  }

  public enum AuthType {
    NONE,
    BASIC,
    OAUTH
  }

  public static final class BasicCfg {
    private String username;
    private String password;

    public String getUsername() {
      return username;
    }

    public void setUsername(final String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(final String password) {
      this.password = password;
    }
  }

  public static final class OAuthConfig {
    private String clientId;
    private String clientSecret;
    private String audience;
    private String authzUrl;

    public String getClientId() {
      return clientId;
    }

    public void setClientId(final String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public String getAudience() {
      return audience;
    }

    public void setAudience(final String audience) {
      this.audience = audience;
    }

    public String getAuthzUrl() {
      return authzUrl;
    }

    public void setAuthzUrl(final String authzUrl) {
      this.authzUrl = authzUrl;
    }
  }
}
