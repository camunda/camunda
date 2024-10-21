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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
