/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import io.camunda.application.Profile;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;

public class EmbeddedGatewayAuthenticationNoneIT
    extends GatewayAuthenticationNoneAbstractIT<TestStandaloneBroker> {

  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withAdditionalProfile(Profile.IDENTITY_AUTH)
          .withProperty("zeebe.broker.gateway.security.authentication.mode", "none")
          .withProperty("camunda.identity.issuerBackendUrl", getKeycloakRealmAddress())
          .withProperty("camunda.identity.audience", ZEEBE_CLIENT_AUDIENCE);

  @Override
  protected TestGateway<TestStandaloneBroker> getGateway() {
    return zeebe;
  }
}
