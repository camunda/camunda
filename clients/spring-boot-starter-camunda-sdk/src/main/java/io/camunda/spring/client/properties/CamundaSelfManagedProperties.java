/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@ConfigurationProperties(prefix = "zeebe")
@Deprecated(forRemoval = true, since = "8.6")
public class CamundaSelfManagedProperties {

  @Value("${zeebe.authorization.server.url:#{null}}")
  private String authServer;

  @Value("${zeebe.client.id:#{null}}")
  private String clientId;

  @Value("${zeebe.client.secret:#{null}}")
  private String clientSecret;

  @Value("${zeebe.token.audience:#{null}}")
  private String audience;

  @Value("${zeebe.client.broker.gatewayAddress:#{null}}")
  private String gatewayAddress;

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.issuer")
  @Deprecated
  public String getAuthServer() {
    return authServer;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-id")
  @Deprecated
  public String getClientId() {
    return clientId;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-secret")
  @Deprecated
  public String getClientSecret() {
    return clientSecret;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.audience")
  @Deprecated
  public String getAudience() {
    return audience;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.grpc-address")
  @Deprecated
  public String getGatewayAddress() {
    return gatewayAddress;
  }
}
