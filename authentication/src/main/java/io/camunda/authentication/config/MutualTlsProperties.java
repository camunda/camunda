/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration properties for mutual TLS (mTLS) authentication. */
@Component
@ConfigurationProperties(prefix = "camunda.security.authentication.mtls")
public class MutualTlsProperties {

  private static final Logger LOG = LoggerFactory.getLogger(MutualTlsProperties.class);

  /** List of paths to trusted CA certificate files for client certificate validation. */
  private List<String> trustedCertificates;

  /** Enable mutual TLS authentication. */
  private boolean enabled =
      Boolean.parseBoolean(
          System.getenv().getOrDefault("CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ENABLED", "false"));

  /**
   * Default roles to assign to authenticated users when no specific roles can be extracted from the
   * certificate.
   */
  private List<String> defaultRoles = List.of("ROLE_USER");

  /** Whether to require valid certificate chain validation against trusted CAs. */
  private boolean requireValidChain = true;

  /** Whether to check certificate revocation status. */
  private boolean checkRevocation = false;

  /** Subject DN pattern to extract username (regex). If not specified, CN is used. */
  private String subjectDnPattern;

  /** Certificate extension OID to extract roles from (e.g., Subject Alternative Name). */
  private String roleExtensionOid;

  /** Whether to enable role mapping from certificate attributes. */
  private boolean enableRoleMapping = false;

  /** Whether to allow self-signed certificates (for development/testing). */
  private boolean allowSelfSigned = true;

  public MutualTlsProperties() {
    final String envVar = System.getenv("CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ENABLED");
    final String allowSelfSignedEnv =
        System.getenv("CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ALLOW_SELF_SIGNED");
    LOG.info(
        "MutualTlsProperties: Constructor - Environment variable CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ENABLED = {}",
        envVar);
    LOG.info("MutualTlsProperties: Constructor - Parsed enabled value = {}", enabled);
    LOG.info(
        "MutualTlsProperties: Constructor - Environment variable CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ALLOW_SELF_SIGNED = {}",
        allowSelfSignedEnv);
    LOG.info(
        "MutualTlsProperties: Constructor - allowSelfSigned default value = {}", allowSelfSigned);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getTrustedCertificates() {
    return trustedCertificates;
  }

  public void setTrustedCertificates(final List<String> trustedCertificates) {
    this.trustedCertificates = trustedCertificates;
  }

  public List<String> getDefaultRoles() {
    return defaultRoles;
  }

  public void setDefaultRoles(final List<String> defaultRoles) {
    this.defaultRoles = defaultRoles;
  }

  public boolean isRequireValidChain() {
    return requireValidChain;
  }

  public void setRequireValidChain(final boolean requireValidChain) {
    this.requireValidChain = requireValidChain;
  }

  public boolean isCheckRevocation() {
    return checkRevocation;
  }

  public void setCheckRevocation(final boolean checkRevocation) {
    this.checkRevocation = checkRevocation;
  }

  public String getSubjectDnPattern() {
    return subjectDnPattern;
  }

  public void setSubjectDnPattern(final String subjectDnPattern) {
    this.subjectDnPattern = subjectDnPattern;
  }

  public String getRoleExtensionOid() {
    return roleExtensionOid;
  }

  public void setRoleExtensionOid(final String roleExtensionOid) {
    this.roleExtensionOid = roleExtensionOid;
  }

  public boolean isEnableRoleMapping() {
    return enableRoleMapping;
  }

  public void setEnableRoleMapping(final boolean enableRoleMapping) {
    this.enableRoleMapping = enableRoleMapping;
  }

  public boolean isAllowSelfSigned() {
    return allowSelfSigned;
  }

  public void setAllowSelfSigned(final boolean allowSelfSigned) {
    this.allowSelfSigned = allowSelfSigned;
  }
}
