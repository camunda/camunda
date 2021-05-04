/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.property;

public class ZeebeProperties {

  private String gatewayAddress = "localhost:26500";

  @Deprecated
  public String getBrokerContactPoint() {
    return gatewayAddress;
  }

  @Deprecated
  public void setBrokerContactPoint(String brokerContactPoint) {
    this.gatewayAddress = brokerContactPoint;
  }

  public String getGatewayAddress() {
    return gatewayAddress;
  }

  public void setGatewayAddress(final String gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
  }

}
