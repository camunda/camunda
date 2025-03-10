/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class SslProperties {

  private String certificatePath;
  private boolean verifyHostname = true;
  private boolean selfSigned = false;

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
