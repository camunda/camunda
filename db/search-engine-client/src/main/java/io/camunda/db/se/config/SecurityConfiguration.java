/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.se.config;

public class SecurityConfiguration {

  private static final boolean ENABLED_DEFAULT = false;
  private static final boolean VERIFY_HOSTNAME_DEFAULT = true;
  private static final boolean SELF_SIGNED_DEFAULT = false;

  private boolean enabled = ENABLED_DEFAULT;
  private String certificatePath;
  private boolean verifyHostname = VERIFY_HOSTNAME_DEFAULT;
  private boolean selfSigned = SELF_SIGNED_DEFAULT;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCertificatePath() {
    return certificatePath;
  }

  public void setCertificatePath(String certificatePath) {
    this.certificatePath = certificatePath;
  }

  public boolean isVerifyHostname() {
    return verifyHostname;
  }

  public void setVerifyHostname(boolean verifyHostname) {
    this.verifyHostname = verifyHostname;
  }

  public boolean isSelfSigned() {
    return selfSigned;
  }

  public void setSelfSigned(boolean selfSigned) {
    this.selfSigned = selfSigned;
  }
}
