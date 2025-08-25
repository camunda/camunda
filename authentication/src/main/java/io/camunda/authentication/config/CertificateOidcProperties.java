/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "camunda.security.authentication.oidc")
public class CertificateOidcProperties {
  @NestedConfigurationProperty private final String clientAssertionKeystorePath;
  private final String clientAssertionKeystorePassword;
  private final String clientAssertionKeystoreKeyAlias;
  private final String clientAssertionKeystoreKeyPassword;

  @ConstructorBinding
  public CertificateOidcProperties(
      @DefaultValue("${CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENT_ASSERTION_KEYSTORE_PATH:}")
          String clientAssertionKeystorePath,
      @DefaultValue("${CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENT_ASSERTION_KEYSTORE_PASSWORD:}")
          String clientAssertionKeystorePassword,
      @DefaultValue("${CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS:}")
          String clientAssertionKeystoreKeyAlias,
      @DefaultValue(
              "${CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD:}")
          String clientAssertionKeystoreKeyPassword) {
    this.clientAssertionKeystorePath = clientAssertionKeystorePath;
    this.clientAssertionKeystorePassword = clientAssertionKeystorePassword;
    this.clientAssertionKeystoreKeyAlias = clientAssertionKeystoreKeyAlias;
    this.clientAssertionKeystoreKeyPassword = clientAssertionKeystoreKeyPassword;
  }

  public String getClientAssertionKeystorePath() {
    return clientAssertionKeystorePath;
  }

  public String getClientAssertionKeystorePassword() {
    return clientAssertionKeystorePassword;
  }

  public String getClientAssertionKeystoreKeyAlias() {
    return clientAssertionKeystoreKeyAlias;
  }

  public String getClientAssertionKeystoreKeyPassword() {
    return clientAssertionKeystoreKeyPassword;
  }

  public boolean isConfigured() {
    return clientAssertionKeystorePath != null
        && !clientAssertionKeystorePath.isEmpty()
        && clientAssertionKeystorePassword != null
        && !clientAssertionKeystorePassword.isEmpty()
        && clientAssertionKeystoreKeyAlias != null
        && !clientAssertionKeystoreKeyAlias.isEmpty()
        && clientAssertionKeystoreKeyPassword != null
        && !clientAssertionKeystoreKeyPassword.isEmpty();
  }
}
