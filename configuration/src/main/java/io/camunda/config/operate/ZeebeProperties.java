/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class ZeebeProperties {

  private String brokerContactPoint;
  private String gatewayAddress = "localhost:26500";
  private boolean isSecure = false;
  private String certificatePath = null;

  public boolean isSecure() {
    return isSecure;
  }

  public ZeebeProperties setSecure(final boolean secure) {
    isSecure = secure;
    return this;
  }

  public String getCertificatePath() {
    return certificatePath;
  }

  public ZeebeProperties setCertificatePath(final String caCertificatePath) {
    certificatePath = caCertificatePath;
    return this;
  }

  @Deprecated
  public String getBrokerContactPoint() {
    return brokerContactPoint;
  }

  @Deprecated
  public void setBrokerContactPoint(final String brokerContactPoint) {
    this.brokerContactPoint = brokerContactPoint;
  }

  public String getGatewayAddress() {
    return gatewayAddress;
  }

  public ZeebeProperties setGatewayAddress(final String gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
    return this;
  }
}
