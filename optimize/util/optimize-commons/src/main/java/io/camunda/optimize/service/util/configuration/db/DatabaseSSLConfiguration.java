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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $enabled = getEnabled();
    result = result * PRIME + ($enabled == null ? 43 : $enabled.hashCode());
    final Object $selfSigned = getSelfSigned();
    result = result * PRIME + ($selfSigned == null ? 43 : $selfSigned.hashCode());
    final Object $certificate = getCertificate();
    result = result * PRIME + ($certificate == null ? 43 : $certificate.hashCode());
    final Object $certificateAuthorities = getCertificateAuthorities();
    result =
        result * PRIME
            + ($certificateAuthorities == null ? 43 : $certificateAuthorities.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DatabaseSSLConfiguration)) {
      return false;
    }
    final DatabaseSSLConfiguration other = (DatabaseSSLConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$enabled = getEnabled();
    final Object other$enabled = other.getEnabled();
    if (this$enabled == null ? other$enabled != null : !this$enabled.equals(other$enabled)) {
      return false;
    }
    final Object this$selfSigned = getSelfSigned();
    final Object other$selfSigned = other.getSelfSigned();
    if (this$selfSigned == null
        ? other$selfSigned != null
        : !this$selfSigned.equals(other$selfSigned)) {
      return false;
    }
    final Object this$certificate = getCertificate();
    final Object other$certificate = other.getCertificate();
    if (this$certificate == null
        ? other$certificate != null
        : !this$certificate.equals(other$certificate)) {
      return false;
    }
    final Object this$certificateAuthorities = getCertificateAuthorities();
    final Object other$certificateAuthorities = other.getCertificateAuthorities();
    if (this$certificateAuthorities == null
        ? other$certificateAuthorities != null
        : !this$certificateAuthorities.equals(other$certificateAuthorities)) {
      return false;
    }
    return true;
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
