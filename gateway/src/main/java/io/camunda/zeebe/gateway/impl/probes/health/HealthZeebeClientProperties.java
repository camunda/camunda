/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.Objects;

public class HealthZeebeClientProperties {

  private Duration requestTimeout;
  private SecurityProperties securityProperties = new SecurityProperties();

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    if (requestTimeout.toMillis() <= 0) {
      throw new IllegalArgumentException("requestTimeout must be a positive value");
    }
    this.requestTimeout = requestTimeout;
  }

  public SecurityProperties getSecurityProperties() {
    return securityProperties;
  }

  public void setSecurityProperties(final SecurityProperties securityProperties) {
    this.securityProperties = requireNonNull(securityProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestTimeout, securityProperties);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HealthZeebeClientProperties that = (HealthZeebeClientProperties) o;
    return Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(securityProperties, that.securityProperties);
  }

  @Override
  public String toString() {
    return "HealthZeebeClientProperties{"
        + "requestTimeout="
        + requestTimeout
        + ", securityProperties="
        + securityProperties
        + '}';
  }

  public static class SecurityProperties {

    private OAuthSecurityProperties oauthSecurityProperties;

    public OAuthSecurityProperties getOauthSecurityProperties() {
      return oauthSecurityProperties;
    }

    public void setOauthSecurityProperties(final OAuthSecurityProperties oauthSecurityProperties) {
      this.oauthSecurityProperties = oauthSecurityProperties;
    }

    @Override
    public int hashCode() {
      return Objects.hash(oauthSecurityProperties);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SecurityProperties that = (SecurityProperties) o;
      return Objects.equals(oauthSecurityProperties, that.oauthSecurityProperties);
    }

    @Override
    public String toString() {
      return "SecurityProperties{" + "oauthSecurityProperties=" + oauthSecurityProperties + '}';
    }

    public static class OAuthSecurityProperties {
      private String clientId;
      private String clientSecret;
      private String audience;
      private URL authorizationServer;
      private String credentialsCachePath;
      private File credentialsCache;
      private Duration connectTimeout;
      private Duration readTimeout;

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

      public URL getAuthorizationServer() {
        return authorizationServer;
      }

      public void setAuthorizationServer(final URL authorizationServer) {
        this.authorizationServer = authorizationServer;
      }

      public String getCredentialsCachePath() {
        return credentialsCachePath;
      }

      public void setCredentialsCachePath(final String credentialsCachePath) {
        this.credentialsCachePath = credentialsCachePath;
      }

      public File getCredentialsCache() {
        return credentialsCache;
      }

      public void setCredentialsCache(final File credentialsCache) {
        this.credentialsCache = credentialsCache;
      }

      public Duration getConnectTimeout() {
        return connectTimeout;
      }

      public void setConnectTimeout(final Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
      }

      public Duration getReadTimeout() {
        return readTimeout;
      }

      public void setReadTimeout(final Duration readTimeout) {
        this.readTimeout = readTimeout;
      }

      @Override
      public int hashCode() {
        return Objects.hash(
            clientId,
            clientSecret,
            audience,
            authorizationServer,
            credentialsCachePath,
            credentialsCache,
            connectTimeout,
            readTimeout);
      }

      @Override
      public boolean equals(final Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        final OAuthSecurityProperties that = (OAuthSecurityProperties) o;
        return Objects.equals(clientId, that.clientId)
            && Objects.equals(clientSecret, that.clientSecret)
            && Objects.equals(audience, that.audience)
            && Objects.equals(authorizationServer, that.authorizationServer)
            && Objects.equals(credentialsCachePath, that.credentialsCachePath)
            && Objects.equals(credentialsCache, that.credentialsCache)
            && Objects.equals(connectTimeout, that.connectTimeout)
            && Objects.equals(readTimeout, that.readTimeout);
      }

      @Override
      public String toString() {
        return "OAuthSecurityProperties{"
            + "clientId='"
            + clientId
            + '\''
            + ", audience='"
            + audience
            + '\''
            + ", authorizationServer="
            + authorizationServer
            + ", credentialsCachePath='"
            + credentialsCachePath
            + '\''
            + ", credentialsCache="
            + credentialsCache
            + ", connectTimeout="
            + connectTimeout
            + ", readTimeout="
            + readTimeout
            + '}';
      }
    }
  }
}
