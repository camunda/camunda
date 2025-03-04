/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import io.camunda.application.Profile;
import io.camunda.zeebe.gateway.impl.configuration.AuthenticationCfg.AuthMode;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;

public class StandaloneGatewayAuthenticationIdentityIT
    extends GatewayAuthenticationIdentityAbstractIT<TestStandaloneGateway> {

  private final TestStandaloneGateway zeebe =
      new TestStandaloneGateway()
          .withAdditionalProfile(Profile.IDENTITY_AUTH)
          .withGatewayConfig(
              cfg -> {
                final var auth = cfg.getSecurity().getAuthentication();
                auth.setMode(AuthMode.IDENTITY);
              })
          .withProperty("zeebe.gateway.enable", "true")
          .withProperty("camunda.identity.issuerBackendUrl", getKeycloakRealmAddress())
          .withProperty("camunda.identity.audience", ZEEBE_CLIENT_AUDIENCE);

  @Override
  protected TestGateway<TestStandaloneGateway> getGateway() {
    return zeebe;
  }
}
