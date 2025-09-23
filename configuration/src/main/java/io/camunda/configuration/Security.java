/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class Security {

  /** Enable security */
  private boolean enabled = false;

  /** Path to certificate used by Elasticsearch or Opensearch */
  private String certificatePath;

  /** Should the hostname be validated */
  private boolean verifyHostname = true;

  /** Certificate was self-signed */
  private boolean selfSigned = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getCertificatePath() {
    return certificatePath;
  }

  public void setCertificatePath(final String certificatePath) {
    this.certificatePath = certificatePath;
  }

  public boolean isVerifyHostname() {
    return verifyHostname;
  }

  public void setVerifyHostname(final boolean verifyHostname) {
    this.verifyHostname = verifyHostname;
  }

  public boolean isSelfSigned() {
    return selfSigned;
  }

  public void setSelfSigned(final boolean selfSigned) {
    this.selfSigned = selfSigned;
  }
}
