/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class ZeebeProperties {

  private String gatewayAddress = "localhost:26500";
  private boolean isSecure = false;
  private String certificatePath = null;
  private String restAddress = "http://localhost:8080";

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
    return gatewayAddress;
  }

  @Deprecated
  public void setBrokerContactPoint(final String brokerContactPoint) {
    gatewayAddress = brokerContactPoint;
  }

  public String getGatewayAddress() {
    return gatewayAddress;
  }

  public ZeebeProperties setGatewayAddress(final String gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
    return this;
  }

  public String getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final String restAddress) {
    this.restAddress = restAddress;
  }
}
