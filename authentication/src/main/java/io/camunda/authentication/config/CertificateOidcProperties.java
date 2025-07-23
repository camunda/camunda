/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for certificate-based OIDC authentication.
 * These properties extend the standard OIDC configuration to support
 * Microsoft Entra ID certificate credentials.
 *
 * Example configuration:
 * <pre>
 * camunda:
 *   security:
 *     authentication:
 *       method: oidc
 *       oidc:
 *         issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0
 *         client-id: {your-client-id}
 *         token-uri: https://login.microsoftonline.com/{tenant-id}/oauth2/v2.0/token
 *         authorization-uri: https://login.microsoftonline.com/{tenant-id}/oauth2/v2.0/authorize
 *         jwk-set-uri: https://login.microsoftonline.com/{tenant-id}/discovery/v2.0/keys
 *         client-assertion-keystore-path: /path/to/certificate.p12
 *         client-assertion-keystore-password: your-keystore-password
 *         client-assertion-keystore-key-alias: certificate-alias
 *         client-assertion-keystore-key-password: your-key-password
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "camunda.security.authentication.oidc")
public class CertificateOidcProperties {
  
  /**
   * Path to the PKCS12 keystore containing the client certificate for authentication.
   * This certificate should be uploaded to Microsoft Entra ID application configuration.
   */
  private String clientAssertionKeystorePath;
  
  /**
   * Password for the PKCS12 keystore.
   */
  private String clientAssertionKeystorePassword;
  
  /**
   * Alias of the certificate within the keystore.
   * If not provided, the first available certificate will be used.
   */
  private String clientAssertionKeystoreKeyAlias;
  
  /**
   * Password for the private key within the keystore.
   * If not provided, the keystore password will be used.
   */
  private String clientAssertionKeystoreKeyPassword;

  public String getClientAssertionKeystorePath() {
    return clientAssertionKeystorePath;
  }

  public void setClientAssertionKeystorePath(final String clientAssertionKeystorePath) {
    this.clientAssertionKeystorePath = clientAssertionKeystorePath;
  }

  public String getClientAssertionKeystorePassword() {
    return clientAssertionKeystorePassword;
  }

  public void setClientAssertionKeystorePassword(final String clientAssertionKeystorePassword) {
    this.clientAssertionKeystorePassword = clientAssertionKeystorePassword;
  }

  public String getClientAssertionKeystoreKeyAlias() {
    return clientAssertionKeystoreKeyAlias;
  }

  public void setClientAssertionKeystoreKeyAlias(final String clientAssertionKeystoreKeyAlias) {
    this.clientAssertionKeystoreKeyAlias = clientAssertionKeystoreKeyAlias;
  }

  public String getClientAssertionKeystoreKeyPassword() {
    return clientAssertionKeystoreKeyPassword;
  }

  public void setClientAssertionKeystoreKeyPassword(final String clientAssertionKeystoreKeyPassword) {
    this.clientAssertionKeystoreKeyPassword = clientAssertionKeystoreKeyPassword;
  }
}
