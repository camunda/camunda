/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public class DatabaseSSLConfiguration {

  private Boolean enabled;
  private Boolean selfSigned;
  private String certificate;

  @JsonProperty("certificate_authorities")
  private List<String> certificateAuthorities;

  public DatabaseSSLConfiguration() {}

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getSelfSigned() {
    return selfSigned;
  }

  public void setSelfSigned(final Boolean selfSigned) {
    this.selfSigned = selfSigned;
  }

  public String getCertificate() {
    return certificate;
  }

  public void setCertificate(final String certificate) {
    this.certificate = certificate;
  }

  public List<String> getCertificateAuthorities() {
    return certificateAuthorities;
  }

  @JsonProperty("certificate_authorities")
  public void setCertificateAuthorities(final List<String> certificateAuthorities) {
    this.certificateAuthorities = certificateAuthorities;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseSSLConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DatabaseSSLConfiguration that = (DatabaseSSLConfiguration) o;
    return Objects.equals(enabled, that.enabled)
        && Objects.equals(selfSigned, that.selfSigned)
        && Objects.equals(certificate, that.certificate)
        && Objects.equals(certificateAuthorities, that.certificateAuthorities);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, selfSigned, certificate, certificateAuthorities);
  }

  @Override
  public String toString() {
    return "DatabaseSSLConfiguration(enabled="
        + getEnabled()
        + ", selfSigned="
        + getSelfSigned()
        + ", certificate="
        + getCertificate()
        + ", certificateAuthorities="
        + getCertificateAuthorities()
        + ")";
  }
}
